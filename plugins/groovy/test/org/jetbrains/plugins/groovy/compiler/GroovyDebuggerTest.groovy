/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.testFramework.ThreadTracker
import com.intellij.testFramework.builders.JavaModuleFixtureBuilder
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import com.intellij.util.SystemProperties
import groovy.transform.CompileStatic
import org.jetbrains.plugins.groovy.GroovyFileType

import java.util.concurrent.TimeUnit

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait

/**
 * @author peter
 */
@CompileStatic
class GroovyDebuggerTest extends GroovyCompilerTestCase implements DebuggerMethods {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.compiler.GroovyDebuggerTest")

  @Override
  Logger getLogger() { LOG }

  @Override
  protected void setUp() {
    super.setUp()
    addGroovyLibrary(myModule)
    enableDebugLogging()
  }

  @Override
  protected void tearDown() throws Exception {
    ThreadTracker.awaitJDIThreadsTermination(100, TimeUnit.SECONDS);
    super.tearDown()
  }

  @Override
  protected boolean runInDispatchThread() {
    return false
  }

  private void enableDebugLogging() {
    TestLoggerFactory.enableDebugLogging(myFixture.testRootDisposable,
                                         "#com.intellij.debugger.engine.DebugProcessImpl",
                                         "#com.intellij.debugger.engine.DebugProcessEvents",
                                         "#org.jetbrains.plugins.groovy.compiler.GroovyDebuggerTest")
    LOG.info(getTestStartedLogMessage())
  }

  private String getTestStartedLogMessage() {
    return "Starting " + getClass().getName() + "." + getTestName(false)
  }

  @Override
  protected void runTest() throws Throwable {
    try {
      super.runTest()
    }
    catch (Throwable e) {
      TestLoggerFactory.dumpLogToStdout(getTestStartedLogMessage())
      throw e
    }
  }

  void runDebugger(PsiFile script, Closure cl) {
    def configuration = createScriptConfiguration(script.virtualFile.path, myModule)
    runDebugger(configuration, cl)
  }

  void testVariableInScript() {
    def file = myFixture.addFileToProject("Foo.groovy", """def a = 2
a""")
    addBreakpoint 'Foo.groovy', 1
    runDebugger file, {
      waitForBreakpoint()
      eval 'a', '2'
      eval '2?:3', '2'
      eval 'null?:3', '3'
    }
  }

  void testVariableInsideClosure() {
    def file = myFixture.addFileToProject("Foo.groovy", """def a = 2
Closure c = {
  a++;
  a    //3
}
c()
a++""")
    addBreakpoint 'Foo.groovy', 3
    runDebugger file, {
      waitForBreakpoint()
      eval 'a', '3'
    }
  }

  void testQualifyNames() {
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


    def file = myFixture.addFileToProject("com/Bar.groovy", """package com
import static com.Goo.*

def lst = [new Foo()] as Set
println 2 //4
""")

    addBreakpoint 'com/Bar.groovy', 4
    make()
    runDebugger file, {
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

  void testCall() {
    def file = myFixture.addFileToProject 'B.groovy', '''class B {
    def getFoo() {2}

    def call(Object... args){
        -1  // 4
    }

    public static void main(String[] args) {
        new B().call()
    }
}'''
    addBreakpoint 'B.groovy', 4
    runDebugger file, {
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

  void testStaticContext() {
    def file = myFixture.addFileToProject 'B.groovy', '''
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
    runDebugger file, {
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
      eval '[0, 1, 2, 3].collect { int numero -> numero.toString() }', '[0, 1, 2, 3]'
    }
  }

  void "test closures in instance context with delegation"() {
    def file = myFixture.addFileToProject 'B.groovy', '''
def cl = { a ->
  hashCode() //2
}
cl.delegate = "string"
cl(42) // 5

def getFoo() { 13 }
'''
    addBreakpoint 'B.groovy', 2
    runDebugger file, {
      waitForBreakpoint()
      eval 'a', '42'
      eval 'size()', '6'
      eval 'delegate.size()', '6'
      eval 'owner.foo', '13'
      eval 'this.foo', '13'
      eval 'foo', '13'
    }
  }

  void testClassOutOfSourceRoots() {
    def tempDir = new TempDirTestFixtureImpl()
    runInEdtAndWait {
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


    runInEdtAndWait {
      myClass = tempDir.createFile("MyClass.groovy", mcText)
    }

    addBreakpoint(myClass, 5)

    def file = myFixture.addFileToProject("Foo.groovy", """
def cl = new GroovyClassLoader()
cl.parseClass('''$mcText''', 'MyClass.groovy').foo(2)
    """)

    runDebugger file, {
      waitForBreakpoint()
      assert myClass == sourcePosition.file.virtualFile
      eval 'a', '2'
    }
  }

  void "test groovy source named java in lib source"() {
    def tempDir = new TempDirTestFixtureImpl()
    runInEdtAndWait {
      tempDir.setUp()
      disposeOnTearDown({ tempDir.tearDown() } as Disposable)
      tempDir.createFile("pkg/java.groovy", "class java {}")
      PsiTestUtil.addLibrary(myModule, 'lib', tempDir.getFile('').path, [] as String[], [''] as String[])
    }

    def facade = JavaPsiFacade.getInstance(project)
    assert !facade.findClass('java', GlobalSearchScope.allScope(project))
    assert !facade.findPackage('').findClassByShortName('java', GlobalSearchScope.allScope(project))

    def file = myFixture.addFileToProject("Foo.groovy", """\
int a = 42
int b = 3 //1
    """)

    addBreakpoint(file.virtualFile, 1)

    runDebugger file, {
      waitForBreakpoint()
      eval 'a', '42'
    }
  }

  void testAnonymousClassInScript() {
    def file = myFixture.addFileToProject('Foo.groovy', '''\
new Runnable() {
  void run() {
    print 'foo'
  }
}.run()

''')
    addBreakpoint 'Foo.groovy', 2
    runDebugger file, {
      waitForBreakpoint()
      eval '1+1', '2'
    }
  }

  void testEvalInStaticMethod() {
    def file = myFixture.addFileToProject('Foo.groovy', '''\
static def foo() {
  int x = 5
  print x
}

foo()

''')
    addBreakpoint 'Foo.groovy', 2
    runDebugger file, {
      waitForBreakpoint()
      eval 'x', '5'
    }
  }

  void "test non-identifier script name"() {
    def file = myFixture.addFileToProject('foo-bar.groovy', '''\
int x = 1
println "hello"
''')
    addBreakpoint file.name, 1
    runDebugger file, {
      waitForBreakpoint()
      eval 'x', '1'
    }
  }

  void "test navigation outside source"() {
    def module1 = addModule("module1", false)
    def module2 = addModule("module2", true)
    addGroovyLibrary(module1)
    addGroovyLibrary(module2)
    runInEdtAndWait {
      ModuleRootModificationUtil.addDependency(myModule, module1)
    }

    def scr = myFixture.addFileToProject('module1/Scr.groovy', 'println "hello"')
    myFixture.addFileToProject('module2/Scr.groovy', 'println "hello"')

    addBreakpoint('module1/Scr.groovy', 0)
    runDebugger(scr) {
      waitForBreakpoint()
      assert scr == sourcePosition.file
    }
  }

  void "test in static inner class"() {
    def file = myFixture.addFileToProject "Foo.groovy", """
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
    runDebugger file, {
      waitForBreakpoint()
      eval 'x', '1'
      eval 'this', 'str'
    }
  }

  void "test evaluation within trait method"() {
    def file = myFixture.addFileToProject 'Foo.groovy', '''
trait Introspector {  // 1
    def whoAmI() {
        this          // 3
    }
}

class FooT implements Introspector {
    def a = 1
    def b = 3

    String toString() { 'fooInstance' }
}

new FooT().whoAmI()
'''
    addBreakpoint 'Foo.groovy', 3
    runDebugger file, {
      waitForBreakpoint()
      eval 'a', '1'
      eval 'b', '3'
      eval 'this', 'fooInstance'
    }
  }

  void "test evaluation in java context"() {
    def starterFile = myFixture.addFileToProject 'Gr.groovy', '''
new Main().foo()
'''
    def file = myFixture.addFileToProject 'Main.java', '''
import java.util.Arrays;
import java.util.List;

public class Main {
  void foo() {
     List<Integer> a = Arrays.asList(1,2,3,4,5,6,7,8,9,10);
     int x = 5; // 7
  }
}
'''
    make()

    addBreakpoint file.virtualFile, 7
    runDebugger starterFile, {
      waitForBreakpoint()
      eval 'a.find {it == 4}', '4', GroovyFileType.GROOVY_FILE_TYPE
    }
  }

  void "test evaluation in static java context"() {
    def starterFile = myFixture.addFileToProject 'Gr.groovy', '''
Main.test()
'''
    def file = myFixture.addFileToProject 'Main.java', '''
import java.util.Arrays;
import java.util.List;

public class Main {
  public static void test() {
     List<Integer> a = Arrays.asList(1,2,3,4,5,6,7,8,9,10);
     int x = 5; // 7
  }
}
'''
    make()

    addBreakpoint file.virtualFile, 7
    runDebugger starterFile, {
      waitForBreakpoint()
      eval 'a.find {it == 6}', '6', GroovyFileType.GROOVY_FILE_TYPE
    }
  }

  void "test evaluation with java references in java context"() {
    def starterFile = myFixture.addFileToProject 'Gr.groovy', '''
new Main().foo()
'''
    def file = myFixture.addFileToProject 'Main.java', '''
import java.util.Arrays;
import java.util.List;

public class Main {
  void foo() {
     List<String> a = Arrays.asList("1","22","333");
     int x = 5; // 7
  }
}
'''
    make()

    addBreakpoint file.virtualFile, 7
    runDebugger starterFile, {
      waitForBreakpoint()
      eval 'a.findAll {it.length() > 2}.size()', '1', GroovyFileType.GROOVY_FILE_TYPE
    }
  }

  void "test evaluation of params in java context"() {
    def starterFile = myFixture.addFileToProject 'Gr.groovy', '''
new Main().foo((String[])["a", "b", "c"])
'''
    def file = myFixture.addFileToProject 'Main.java', '''
import java.util.Arrays;
import java.util.List;

public class Main {
  void foo(String[] a) {
     int x = 5; // 6
  }
}
'''
    make()

    addBreakpoint file.virtualFile, 6
    runDebugger starterFile, {
      waitForBreakpoint()
      eval 'a[1]', 'b', GroovyFileType.GROOVY_FILE_TYPE
    }
  }

  void addBreakpoint(String fileName, int line) {
    VirtualFile file = null
    runInEdtAndWait {
      file = myFixture.tempDirFixture.getFile(fileName)
    }
    addBreakpoint(file, line)
  }
}
