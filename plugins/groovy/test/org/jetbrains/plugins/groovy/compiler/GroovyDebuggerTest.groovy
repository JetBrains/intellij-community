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
import com.intellij.execution.executors.DefaultDebugExecutor
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.util.SystemProperties
import com.intellij.util.concurrency.Semaphore
import org.jetbrains.plugins.groovy.debugger.GroovyPositionManager

/**
 * @author peter
 */
class GroovyDebuggerTest extends GroovyCompilerTestCase {

  @Override
  protected void setUp() {
    edt {
      super.setUp()
      addGroovyLibrary(myModule);
    }

    GroovyPositionManager.registerPositionManager(project)
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
    boolean trace = name == 'testClassOutOfSourceRoots'
    make()
    edt {
      ProgramRunner runner = ProgramRunner.PROGRAM_RUNNER_EP.extensions.find { it.class == GenericDebuggerRunner }
      runProcess(mainClass, myModule, DefaultDebugExecutor, [onTextAvailable: { evt, type -> if (trace) print evt.text}] as ProcessAdapter, runner)
    }
    cl.call()
    if (trace) {
      println "terminated1?: " + debugProcess.executionResult.processHandler.isProcessTerminated()
    }
    resume()
    debugProcess.executionResult.processHandler.waitFor()
    if (trace) {
      println "terminated2?: " + debugProcess.executionResult.processHandler.isProcessTerminated()
    }
  }

  public void testSimpleEvaluate() {
    myFixture.addFileToProject("Foo.groovy", "println 'hello'");
    addBreakpoint 'Foo.groovy', 0
    runDebugger 'Foo', {
      waitForBreakpoint()
      eval '2?:3', '2'
      eval 'null?:3', '3'
    }
  }

  public void testVariableInScript() {
    myFixture.addFileToProject("Foo.groovy", """def a = 2
a""");
    addBreakpoint 'Foo.groovy', 1
    runDebugger 'Foo', {
      waitForBreakpoint()
      eval 'a', '2'
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

  public void testQualifyClassNames() {
    myFixture.addFileToProject("com/Foo.groovy", """
package com
class Foo { static bar = 2 }""")


    myFixture.addFileToProject("com/Bar.groovy", """package com
println 2""")

    addBreakpoint 'com/Bar.groovy', 1
    runDebugger 'com.Bar', {
      waitForBreakpoint()
      eval 'Foo.bar', '2'
    }
  }

  public void testClassOutOfSourceRoots() {
    def tempDir = new TempDirTestFixtureImpl()
    edt {
      tempDir.setUp()
      disposeOnTearDown({ tempDir.tearDown() } as Disposable)
      ApplicationManager.application.runWriteAction {
        def model = ModuleRootManager.getInstance(myModule).modifiableModel
        model.addContentEntry(tempDir.getFile(''))
        model.commit()
      }
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
      println 'on a breakpoint'
      SourcePosition position = managed {
        EvaluationContextImpl context = evaluationContext()
        println "evalCtx: $context.frameProxy $context.thisObject"
        ContextUtil.getSourcePosition(context)
      }
      println "position $position"
      assert myClass == position.file.virtualFile
      eval 'a', '2'
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
    debugProcess.managerThread.invoke(debugProcess.createResumeCommand(debugProcess.suspendManager.pausedContext))
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
    debugProcess.managerThread.invokeAndWait(new DebuggerContextCommandImpl(ctx) {
      @Override
      void threadAction() {
        result = cl()
      }
    })
    return result
  }

  private String eval(final String codeText, String expected) throws EvaluateException {

    Semaphore semaphore = new Semaphore()
    semaphore.down()
    semaphore.down()

    EvaluationContextImpl ctx
    def item = new WatchItemDescriptor(project, new TextWithImportsImpl(CodeFragmentKind.EXPRESSION, codeText))
    managed {
      ctx = evaluationContext()
      item.setContext(ctx)
      item.updateRepresentation(ctx, { semaphore.up() } as DescriptorLabelListener)
    }
    assert semaphore.waitFor(10000):  "too long evaluation: $item.label"

    String result = managed { DebuggerUtils.getValueAsString(ctx, item.value) }
    assert result == expected
  }

  private EvaluationContextImpl evaluationContext() {
    final SuspendContextImpl suspendContext = debugProcess.suspendManager.pausedContext
    new EvaluationContextImpl(suspendContext, suspendContext.frameProxy, suspendContext.frameProxy.thisObject())
  }
}
