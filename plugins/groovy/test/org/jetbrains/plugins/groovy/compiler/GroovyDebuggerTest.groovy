/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.compiler
import com.intellij.debugger.SourcePosition
import com.intellij.debugger.engine.ContextUtil
import com.intellij.debugger.engine.DebugProcessImpl
import com.intellij.debugger.engine.DebuggerUtils
import com.intellij.debugger.engine.SuspendContextImpl
import com.intellij.debugger.engine.evaluation.CodeFragmentKind
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.engine.evaluation.EvaluationContextImpl
import com.intellij.debugger.engine.evaluation.TextWithImportsImpl
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl
import com.intellij.debugger.impl.DebuggerContextUtil
import com.intellij.debugger.impl.DebuggerManagerImpl
import com.intellij.debugger.impl.DebuggerSession
import com.intellij.debugger.impl.GenericDebuggerRunner
import com.intellij.debugger.ui.DebuggerPanelsManager
import com.intellij.debugger.ui.impl.watch.WatchItemDescriptor
import com.intellij.debugger.ui.tree.render.DescriptorLabelListener
import com.intellij.execution.configurations.RunProfile
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.OSProcessManager
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.impl.DebugUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.Semaphore
/**
 * @author peter
 */
class GroovyDebuggerTest extends GroovyCompilerTestCase {
  @Override
  protected boolean useJps() { false }

  @Override
  protected void setUp() {
    edt {
      super.setUp()
      addGroovyLibrary(myModule);
    }

  }

  @Override
  protected boolean runInDispatchThread() {
    return false
  }

  @Override
  protected void invokeTestRunnable(Runnable runnable) {
    runnable.run()
  }

  @Override
  protected void tearDown() {
    super.tearDown()
  }

  @Override
  protected void tuneFixture(JavaModuleFixtureBuilder moduleBuilder) {
    super.tuneFixture(moduleBuilder)
    def javaHome = FileUtil.toSystemIndependentName(SystemProperties.getJavaHome())
    moduleBuilder.addJdk(StringUtil.trimEnd(StringUtil.trimEnd(javaHome, '/'), '/jre'))
  }

  private void runDebugger(String mainClass, Closure cl) {
    runDebugger(createApplicationConfiguration(mainClass, myModule), cl)
  }
  private void runDebugger(RunProfile configuration, Closure cl) {
    make()
    edt {
      ProgramRunner runner = ProgramRunner.PROGRAM_RUNNER_EP.extensions.find { it.class == GenericDebuggerRunner }
      def listener = [onTextAvailable: { evt, type -> }] as ProcessAdapter
      runConfiguration(DefaultDebugExecutor, listener, runner, configuration);
    }
    try {
      cl.call()
    }
    finally {
      def handler = debugProcess.executionResult.processHandler
      resume()
      if (!handler.waitFor(20000)) {
        if (handler instanceof OSProcessHandler) {
          OSProcessManager.instance.killProcessTree(handler.process)
        } else {
          println "can't terminate $handler"
        }
        fail('too long waiting for process termination')
      }
    }
  }

  public void testVariableInScript() {
    myFixture.addFileToProject("Foo.groovy", """def a = 2
a""");
    addBreakpoint 'Foo.groovy', 1
    runDebugger 'Foo', {
      waitForBreakpoint()
      eval 'a', '2'
      eval '2?:3', '2'
      eval 'null?:3', '3'
    }
  }

  public void testVariableInsideClosure() {
    myFixture.addFileToProject("Foo.groovy", """def a = 2
Closure c = {
  a++;
  a    //3
}
c()
a++""");
    addBreakpoint 'Foo.groovy', 3
    runDebugger 'Foo', {
      waitForBreakpoint()
      eval 'a', '3'
    }
  }

  public void testQualifyNames() {
    myFixture.addFileToProject "com/Goo.groovy", '''
package com
interface Goo {
  int mainConstant = 42
  int secondConstant = 1
}
'''
    myFixture.addFileToProject("com/Foo.groovy", """
package com
class Foo {
  static bar = 2
  int field = 3

  String toString() { field as String }
}""")


    myFixture.addFileToProject("com/Bar.groovy", """package com
import static com.Goo.*

def lst = [new Foo()] as Set
println 2 //4
""")

    addBreakpoint 'com/Bar.groovy', 4
    runDebugger 'com.Bar', {
      waitForBreakpoint()
      eval 'Foo.bar', '2'
      eval 'mainConstant', '42'
      eval 'secondConstant', '1'
      eval 'mainConstant - secondConstant', '41'
      eval '(lst as List<Foo>)[0].field', '3'
      eval 'lst', '[3]'
      eval 'lst.size()', '1'
    }
  }

  public void testCall() {
    myFixture.addFileToProject 'B.groovy', '''class B {
    def getFoo() {2}

    def call(Object... args){
        -1  // 4
    }

    public static void main(String[] args) {
        new B().call()
    }
}'''
    addBreakpoint 'B.groovy', 4
    runDebugger 'B', {
      waitForBreakpoint()
      eval 'foo', '2'
      eval 'getFoo()', '2'
      eval 'this.getFoo()', '2'
      eval 'this.foo', '2'
      eval 'this.call(2)', '-1'
      eval 'call(2)', '-1'
      eval 'call(foo)', '-1'
    }

  }

  public void testStaticContext() {
    myFixture.addFileToProject 'B.groovy', '''
class B {
    public static void main(String[] args) {
        def cl = { a ->
          hashCode() //4
        }
        cl.delegate = "string"
        cl(42) //7
    }
}'''
    addBreakpoint 'B.groovy', 4
    addBreakpoint 'B.groovy', 7
    runDebugger 'B', {
      waitForBreakpoint()
      eval 'args.size()', '0'
      eval 'cl.delegate.size()', '6'
      resume()
      waitForBreakpoint()
      eval 'a', '42'
      eval 'size()', '6'
      eval 'delegate.size()', '6'
      eval 'owner.name', 'B'
      eval 'this.name', 'B'
    }

  }

  public void "test closures in instance context with delegation"() {
    myFixture.addFileToProject 'B.groovy', '''
def cl = { a ->
  hashCode() //2
}
cl.delegate = "string"
cl(42) // 5

def getFoo() { 13 }
'''
    addBreakpoint 'B.groovy', 2
    runDebugger 'B', {
      waitForBreakpoint()
      eval 'a', '42'
      eval 'size()', '6'
      eval 'delegate.size()', '6'
      eval 'owner.foo', '13'
      eval 'this.foo', '13'
      eval 'foo', '13'
    }

  }

  public void testClassOutOfSourceRoots() {
    def tempDir = new TempDirTestFixtureImpl()
    edt {
      tempDir.setUp()
      disposeOnTearDown({ tempDir.tearDown() } as Disposable)
      PsiTestUtil.addContentRoot(myModule, tempDir.getFile(''))
    }

    VirtualFile myClass = null

    def mcText = """
package foo //1

class MyClass { //3
static def foo(def a) {
  println a //5
}
}
"""


    edt {
      myClass = tempDir.createFile("MyClass.groovy", mcText)
    }

    addBreakpoint(myClass, 5)

    myFixture.addFileToProject("Foo.groovy", """
def cl = new GroovyClassLoader()
cl.parseClass('''$mcText''', 'MyClass.groovy').foo(2)
    """)
    make()

    runDebugger 'Foo', {
      waitForBreakpoint()
      assert myClass == sourcePosition.file.virtualFile
      eval 'a', '2'
    }
  }

  private SourcePosition getSourcePosition() {
    managed {
      EvaluationContextImpl context = evaluationContext()
      Computable<SourcePosition> a = { ContextUtil.getSourcePosition(context) } as Computable<SourcePosition>
      return ApplicationManager.getApplication().runReadAction(a)
    }
  }

  void testAnonymousClassInScript() {
    myFixture.addFileToProject('Foo.groovy', '''\
new Runnable() {
  void run() {
    print 'foo'
  }
}.run()

''')
    addBreakpoint 'Foo.groovy', 2
    runDebugger 'Foo', {
      waitForBreakpoint()
      eval '1+1', '2'
    }
  }

  void testEvalInStaticMethod() {
    myFixture.addFileToProject('Foo.groovy', '''\
static def foo() {
  int x = 5
  print x
}

foo()

''')
    addBreakpoint 'Foo.groovy', 2
    runDebugger 'Foo', {
      waitForBreakpoint()
      eval 'x', '5'
    }
  }

  public void "test navigation to script outside source root"() {
    def module1 = addModule("module1", false)
    def module2 = addModule("module2", true)
    addGroovyLibrary(module1)
    addGroovyLibrary(module2)
    edt {
      ModuleRootModificationUtil.addDependency(myModule, module1)
    }

    def scr = myFixture.addFileToProject('module1/Scr.groovy', 'println "hello"')
    myFixture.addFileToProject('module2/Scr.groovy', 'println "hello"')

    addBreakpoint('module1/Scr.groovy', 0)
    runDebugger(createScriptConfiguration(scr.virtualFile.path, myModule)) {
      waitForBreakpoint()
      assert scr == sourcePosition.file
    }
  }

  public void "test in static inner class"() {
    myFixture.addFileToProject "Foo.groovy", """
class Outer {               //1
    static class Inner {
        def x = 1

        def test2() {
            println x       //6
        }

        String toString() { 'str' }
    }

    def test() {
        def z = new Inner()

        println z.x
        z.test2()
    }
}

public static void main(String[] args) {
    new Outer().test()
}
"""
    addBreakpoint('Foo.groovy', 6)
    runDebugger 'Foo', {
      waitForBreakpoint()
      eval 'x', '1'
      eval 'this', 'str'
    }
  }

  private def addBreakpoint(String fileName, int line) {
    VirtualFile file = null
    edt {
      file = myFixture.tempDirFixture.getFile(fileName)
    }
    addBreakpoint(file, line)
  }

  private def addBreakpoint(VirtualFile file, int line) {
    edt {
      DebuggerManagerImpl.getInstanceEx(project).breakpointManager.addLineBreakpoint(FileDocumentManager.instance.getDocument(file), line)
    }
  }

  private def resume() {
    debugProcess.managerThread.invokeAndWait(debugProcess.createResumeCommand(debugProcess.suspendManager.pausedContext))
  }

  private SuspendContextImpl waitForBreakpoint() {
    int i = 0
    def suspendManager = debugProcess.suspendManager
    while (i++ < 1000 && !suspendManager.pausedContext && !debugProcess.executionResult.processHandler.processTerminated) {
      Thread.sleep(10)
    }

    def context = suspendManager.pausedContext
    assert context : "too long process, terminated=$debugProcess.executionResult.processHandler.processTerminated"
    return context
  }

  private DebugProcessImpl getDebugProcess() {
    return getDebugSession().process
  }

  private DebuggerSession getDebugSession() {
    return DebuggerPanelsManager.getInstance(project).sessionTab.session
  }

  private <T> T managed(Closure cl) {
    def result = null
    def ctx = DebuggerContextUtil.createDebuggerContext(debugSession, debugProcess.suspendManager.pausedContext)
    Semaphore semaphore = new Semaphore()
    semaphore.down()
    debugProcess.managerThread.invokeAndWait(new DebuggerContextCommandImpl(ctx) {
      @Override
      void threadAction() {
        result = cl()
        semaphore.up()
      }

      @Override
      protected void commandCancelled() {
        println DebugUtil.currentStackTrace()
      }
    })
    def finished = semaphore.waitFor(20000)
    assert finished : 'Too long debugger action'
    return result
  }

  private void eval(final String codeText, String expected) throws EvaluateException {
    Semaphore semaphore = new Semaphore()
    semaphore.down()

    EvaluationContextImpl ctx
    def item = new WatchItemDescriptor(project, new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, codeText))
    managed {
      ctx = evaluationContext()
      item.setContext(ctx)
      item.updateRepresentation(ctx, { } as DescriptorLabelListener)
      semaphore.up()
    }
    assert semaphore.waitFor(10000):  "too long evaluation: $item.label $item.evaluateException"

    String result = managed { DebuggerUtils.getValueAsString(ctx, item.value) }
    assert result == expected
  }

  private EvaluationContextImpl evaluationContext() {
    final SuspendContextImpl suspendContext = debugProcess.suspendManager.pausedContext
    new EvaluationContextImpl(suspendContext, suspendContext.frameProxy, suspendContext.frameProxy.thisObject())
  }
}
