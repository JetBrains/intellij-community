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

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.CompilerConfigurationImpl
import com.intellij.compiler.server.BuildManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.compiler.options.ExcludeEntryDescription
import com.intellij.openapi.compiler.options.ExcludesConfiguration
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.psi.PsiFile
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestLoggerFactory
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.plugins.groovy.config.GroovyFacetUtil
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

/**
 * @author peter
 */
@CompileStatic
abstract class GroovyCompilerTest extends GroovyCompilerTestCase {
  @Override protected void setUp() {
    super.setUp()
    Logger.getInstance("#org.jetbrains.plugins.groovy.compiler.GroovyCompilerTest").info(testStartMessage)
    addGroovyLibrary(myModule)
  }

  void testPlainGroovy() throws Throwable {
    myFixture.addFileToProject("A.groovy", "println '239'")
    assertEmpty(make())
    assertOutput("A", "239")
  }

  void testJavaDependsOnGroovy() throws Throwable {
    myFixture.addClass("public class Foo {" +
                       "public static void main(String[] args) { " +
                       "  System.out.println(new Bar().foo());" +
                       "}" +
                       "}")
    myFixture.addFileToProject("Bar.groovy", "class Bar {" +
                                             "  def foo() {" +
                                             "    239" +
                                             "  }" +
                                             "}")
    make()
    assertOutput("Foo", "239")
  }

  void testCorrectFailAndCorrect() throws Exception {
    myFixture.addClass("public class Foo {" +
                       "public static void main(String[] args) { " +
                       "  System.out.println(new Bar().foo());" +
                       "}" +
                       "}")
    final String barText = "class Bar {" + "  def foo() { 239  }" + "}"
    final PsiFile file = myFixture.addFileToProject("Bar.groovy", barText)
    make()
    assertOutput("Foo", "239")

    setFileText(file, "class Bar {}")
    shouldFail { make() }

    setFileText(file, barText)
    make()
    assertOutput("Foo", "239")
  }

  void testRenameToJava() throws Throwable {
    myFixture.addClass("public class Foo {" +
                       "public static void main(String[] args) { " +
                       "  System.out.println(new Bar().foo());" +
                       "}" +
                       "}")

    final PsiFile bar =
      myFixture.addFileToProject("Bar.groovy", "public class Bar {" + "public int foo() { " + "  return 239;" + "}" + "}")

    make()
    assertOutput("Foo", "239")

    setFileName bar, "Bar.java"

    make()
    assertOutput("Foo", "239")
  }

  void testTransitiveJavaDependency() throws Throwable {
    final VirtualFile ifoo = myFixture.addClass("public interface IFoo { int foo(); }").getContainingFile().getVirtualFile()
    myFixture.addClass("public class Foo implements IFoo {" +
                       "  public int foo() { return 239; }" +
                       "}")
    final PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar {" +
                                                                 "Foo foo\n" +
                                                                 "public static void main(String[] args) { " +
                                                                 "  System.out.println(new Foo().foo());" +
                                                                 "}" +
                                                                 "}")
    assertEmpty(make())
    assertOutput("Bar", "239")

    touch(ifoo)
    touch(bar.getVirtualFile())

    //assertTrue(assertOneElement(make()).contains("WARNING: Groovyc stub generation failed"));
    assertEmpty make()
    assertOutput("Bar", "239")
  }

  void testTransitiveJavaDependencyThroughGroovy() throws Throwable {
    myFixture.addClass("public class IFoo { void foo() {} }").getContainingFile().getVirtualFile()
    myFixture.addFileToProject("Foo.groovy", "class Foo {\n" +
                                             "  static IFoo f\n" +
                                             "  public int foo() { return 239; }\n" +
                                             "}")
    final PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar extends Foo {" +
                                                                 "public static void main(String[] args) { " +
                                                                 "  System.out.println(new Foo().foo());" +
                                                                 "}" +
                                                                 "}")
    assertEmpty(make())
    assertOutput("Bar", "239")

    deleteClassFile("IFoo")
    touch(bar.getVirtualFile())

    //assertTrue(assertOneElement(make()).contains("WARNING: Groovyc error"));
    assertEmpty make()
    assertOutput("Bar", "239")
  }

  void testTransitiveGroovyDependency() throws Throwable {
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo {} ')
    def bar = myFixture.addFileToProject('Bar.groovy', 'class Bar extends Foo {}')
    def goo = myFixture.addFileToProject('Goo.groovy', 'class Goo extends Bar {}')
    assertEmpty(make())

    touch(foo.virtualFile)
    touch(goo.virtualFile)
    assertEmpty(make())
  }

  void testJavaDependsOnGroovyEnum() throws Throwable {
    myFixture.addFileToProject("Foo.groovy", "enum Foo { FOO }")
    myFixture.addClass("class Bar { Foo f; }")
    assertEmpty(make())
  }

  void testDeleteTransitiveJavaClass() throws Throwable {
    myFixture.addClass("public interface IFoo { int foo(); }")
    myFixture.addClass("public class Foo implements IFoo {" +
                       "  public int foo() { return 239; }" +
                       "}")
    final PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar {" +
                                                                 "Foo foo\n" +
                                                                 "public static void main(String[] args) { " +
                                                                 "  System.out.println(new Foo().foo());" +
                                                                 "}" +
                                                                 "}")
    assertEmpty(make())
    assertOutput("Bar", "239")

    deleteClassFile("IFoo")
    touch(bar.getVirtualFile())

    //assertTrue(assertOneElement(make()).contains("WARNING: Groovyc stub generation failed"));
    assertEmpty make()
    assertOutput("Bar", "239")
  }

  void testGroovyDependsOnGroovy() throws Throwable {
    myFixture.addClass("public class JustToMakeGroovyGenerateStubs {}")
    myFixture.addFileToProject("Foo.groovy", "class Foo { }")
    final PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar {" +
                                                                 "def foo(Foo f) {}\n" +
                                                                 "public static void main(String[] args) { " +
                                                                 "  System.out.println(239);" +
                                                                 "}" +
                                                                 "}")
    assertEmpty(make())
    assertOutput("Bar", "239")

    touch(bar.getVirtualFile())

    assertEmpty(make())
    assertOutput("Bar", "239")
  }

  @Override
  void runBare() {
    new File(TestLoggerFactory.testLogDir, "../log/build-log/build.log").delete()
    super.runBare()
  }

  String getTestStartMessage() { "Starting " + getClass().name + " " + getName() }

  @Override
  void runTest() {
    try {
      super.runTest()
    }
    catch (Throwable e) {
      printLogs()
      throw e
    }
  }

  private void printLogs() {
    println "Idea log"
    TestLoggerFactory.dumpLogToStdout(getTestStartMessage())

    def makeLog = new File(TestLoggerFactory.testLogDir, "../log/build-log/build.log")
    if (makeLog.exists()) {
      println "\n\nServer Log:"
      println makeLog.text
    }
    System.out.flush()
  }

  void testMakeInTests() throws Throwable {
    setupTestSources()
    myFixture.addFileToProject("tests/Super.groovy", "class Super {}")
    assertEmpty(make())

    def sub = myFixture.addFileToProject("tests/Sub.groovy", "class Sub {\n" +
      "  Super xxx() {}\n" +
      "  static void main(String[] args) {" +
      "    println 'hello'" +
      "  }" +
      "}")

    def javaFile = myFixture.addFileToProject("tests/Java.java", "public class Java {}")

    assertEmpty(make())
    assertOutput("Sub", "hello")
  }

  void testTestsDependOnProduction() throws Throwable {
    setupTestSources()
    myFixture.addFileToProject("src/com/Bar.groovy", "package com\n" +
                                                     "class Bar {}")
    myFixture.addFileToProject("src/com/ToGenerateStubs.java", "package com;\n" +
                                                               "public class ToGenerateStubs {}")
    myFixture.addFileToProject("tests/com/BarTest.groovy", "package com\n" +
                                                           "class BarTest extends Bar {}")
    assertEmpty(make())
  }

  void testStubForGroovyExtendingJava() throws Exception {
    def foo = myFixture.addFileToProject("Foo.groovy", "class Foo extends Goo { }")
    myFixture.addFileToProject("Goo.groovy", "class Goo extends Main { void bar() { println 'hello' } }")
    def main = myFixture.addClass("public class Main { public static void main(String[] args) { new Goo().bar(); } }")

    assertEmpty(make())
    assertOutput 'Main', 'hello'

    touch(foo.virtualFile)
    touch(main.containingFile.virtualFile)
    assertEmpty(make())

    assertOutput 'Main', 'hello'
  }

  void testDontApplyTransformsFromSameModule() throws Exception {
    addTransform()

    myFixture.addClass("public class JavaClassToGenerateStubs {}")

    assertEmpty(make())

  }

  private void addTransform() throws IOException {
    myFixture.addFileToProject("Transf.java", """
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.control.*;
import org.codehaus.groovy.transform.*;
@GroovyASTTransformation(phase = CompilePhase.CONVERSION)
public class Transf implements ASTTransformation {
  public void visit(ASTNode[] nodes, SourceUnit sourceUnit) {
    ModuleNode module = (ModuleNode)nodes[0];
    for (ClassNode clazz : module.getClasses()) {

      if (clazz.getName().contains("Bar")) {
        module.addStaticStarImport("Foo", ClassHelper.makeWithoutCaching(Foo.class));
      }
    }
      //throw new RuntimeException("In class " + nodes[0]);
  }
}""")

    myFixture.addFileToProject("Foo.java", "public class Foo {\n" +
                                             "public static int autoImported() { return 239; }\n" +
                                             "}")

    CompilerConfiguration.getInstance(getProject()).addResourceFilePattern("*.ASTTransformation")

    myFixture.addFileToProject("META-INF/services/org.codehaus.groovy.transform.ASTTransformation", "Transf")
  }

  void testApplyTransformsFromDependencies() throws Exception {
    addTransform()

    myFixture.addFileToProject("dependent/Bar.groovy", "class Bar {\n" +
                                                       "  static Object zzz = autoImported()\n" +
                                                       "  static void main(String[] args) {\n" +
                                                       "    println zzz\n" +
                                                       "  }\n" +
                                                       "}")

    myFixture.addFileToProject("dependent/AJavaClass.java", "class AJavaClass {}")

    Module dep = addDependentModule()

    addGroovyLibrary(dep)

    assertEmpty(make())
    assertOutput("Bar", "239", dep)
  }

  void testIndirectDependencies() throws Exception {
    myFixture.addFileToProject("dependent1/Bar1.groovy", "class Bar1 {}")
    myFixture.addFileToProject("dependent2/Bar2.groovy", "class Bar2 extends Bar1 {}")
    PsiFile main = myFixture.addFileToProject("Main.groovy", "class Main extends Bar2 {}")

    Module dep1 = addModule('dependent1', true)
    Module dep2 = addModule('dependent2', true)
    ModuleRootModificationUtil.addDependency dep2, dep1
    ModuleRootModificationUtil.addDependency myModule, dep2

    addGroovyLibrary(dep1)
    addGroovyLibrary(dep2)

    assertEmpty(make())

    touch(main.virtualFile)
    assertEmpty(make())
  }

  void testExtendFromGroovyAbstractClass() throws Exception {
    myFixture.addFileToProject "Super.groovy", "abstract class Super {}"
    myFixture.addFileToProject "AJava.java", "public class AJava {}"
    assertEmpty make()

    myFixture.addFileToProject "Sub.groovy", "class Sub extends Super {}"
    assertEmpty make()
  }

  void test1_7InnerClass() throws Exception {
    myFixture.addFileToProject "Foo.groovy", """
class Foo {
  static class Bar {}
}"""
    def javaFile = myFixture.addFileToProject("AJava.java", "public class AJava extends Foo.Bar {}")
    assertEmpty make()

    touch(javaFile.virtualFile)
    assertEmpty make()
  }

  void testRecompileDependentClass() throws Exception {
    def cloud = myFixture.addFileToProject("Cloud.groovy", """
class Cloud {
  def accessFooProperty(Foo c) {
    c.prop = 2
  }
}
""")
    myFixture.addFileToProject "Foo.groovy", """
class Foo {
  def withGooParameter(Goo x) {}
}"""
    def goo = myFixture.addFileToProject("Goo.groovy", "class Goo {}")

    assertEmpty make()

    touch(cloud.virtualFile)
    touch(goo.virtualFile)
    assertEmpty make()
  }

  void testRecompileExpressionReferences() throws Exception {
    def rusCon = myFixture.addFileToProject('RusCon.groovy', '''
interface RusCon {
  Closure foo = { Seq.foo() }
}''')
    myFixture.addFileToProject "Seq.groovy", """
class Seq implements RusCon {
  static def foo() { }
}"""
    assertEmpty make()

    touch(rusCon.virtualFile)
    assertEmpty make()
  }

  void testRecompileImportedClass() throws Exception {
    def bar = myFixture.addFileToProject("pack/Bar.groovy", """
package pack
import pack.Foo
class Bar {}
""")
    myFixture.addFileToProject "pack/Foo.groovy", """
package pack
class Foo extends Goo {
}"""
    def goo = myFixture.addFileToProject("pack/Goo.groovy", """
package pack
class Goo {}""")

    assertEmpty make()

    touch(bar.virtualFile)
    touch(goo.virtualFile)
    assertEmpty make()
  }

  void testRecompileDependentClassesWithOnlyOneChanged() throws Exception {
    def bar = myFixture.addFileToProject("Bar.groovy", """
class Bar {
  Foo f
}
""")
    myFixture.addFileToProject "Foo.groovy", """
class Foo extends Bar {
}"""

    assertEmpty make()

    touch(bar.virtualFile)
    assertEmpty make()
  }

  void testDollarGroovyInnerClassUsagesInStubs() throws Exception {
    def javaFile = myFixture.addClass("""
      public class JavaClass {
        public static class InnerJavaClass {}
      }
""")
    myFixture.addFileToProject("WithInner.groovy", """
class WithInner {
  static class Inner {}
}
""")
    assertEmpty make()

    myFixture.addFileToProject("Usage.groovy", """
class Usage {
  def foo(WithInner.Inner i) {}
  def foo(JavaClass.InnerJavaClass i) {}
}
""")

    touch(javaFile.containingFile.virtualFile)
    assertEmpty make()
  }

  void testDollarGroovyInnerClassUsagesInStubs2() throws Exception {
    myFixture.addClass(""" public class JavaClass { } """)
    myFixture.addFileToProject("WithInner.groovy", """
class WithInner {
  static class Inner {}
}
""")

    myFixture.addFileToProject("Usage.groovy", """
class Usage {
  def foo(WithInner.Inner i) {}
}
""")

    assertEmpty make()
  }

  void testGroovyAnnotations() {
    myFixture.addClass 'public @interface Anno { Class<?>[] value(); }'
    myFixture.addFileToProject 'Foo.groovy', '@Anno([String]) class Foo {}'
    myFixture.addFileToProject 'Bar.java', 'class Bar extends Foo {}'

    assertEmpty make()
  }

  void testGenericStubs() {
    myFixture.addFileToProject 'Foo.groovy', 'class Foo { List<String> list }'
    myFixture.addFileToProject 'Bar.java', 'class Bar {{ for (String s : new Foo().getList()) { s.hashCode(); } }}'
    assertEmpty make()
  }

  void testDuplicateClassDuringCompilation() throws Exception {
    def base = myFixture.addFileToProject('p/Base.groovy', 'package p; class Base { }').virtualFile
    myFixture.addFileToProject('p/Indirect.groovy', '''package p
class Indirect {
  private static class Inner { Base b }

  private Indirect.Inner foo(Indirect.Inner g1, Inner g2, Base b) {}
 }''').virtualFile
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo { p.Indirect foo() {} }').virtualFile
    assertEmpty make()

    touch(foo)
    touch(base)
    assertEmpty make()
  }

  void testDontRecompileUnneeded() {
    myFixture.addFileToProject('Base.groovy', 'class Base { }')
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo extends Base { }').virtualFile
    myFixture.addFileToProject('Bar.groovy', 'class Bar extends Foo { }')
    def main = myFixture.addFileToProject('Main.groovy', 'class Main extends Bar { }').virtualFile
    assertEmpty make()
    long oldBaseStamp = findClassFile("Base").timeStamp
    long oldMainStamp = findClassFile("Main").timeStamp

    touch(main)
    touch(foo)
    assertEmpty make()
    assert oldMainStamp != findClassFile("Main").timeStamp
    assert oldBaseStamp == findClassFile("Base").timeStamp
  }

  void testPartialCrossRecompile() {
    def used = myFixture.addFileToProject('Used.groovy', 'class Used { }')
    def java = myFixture.addFileToProject('Java.java', 'class Java { void foo(Used used) {} }')
    def main = myFixture.addFileToProject('Main.groovy', 'class Main extends Java {  }').virtualFile

    assertEmpty compileModule(myModule)

    touch(used.virtualFile)
    touch(main)
    assertEmpty make()

    assertEmpty compileModule(myModule)
    assertEmpty compileModule(myModule)

    setFileText(used, 'class Used2 {}')
    shouldFail { make() }
    assert findClassFile('Used') == null

    setFileText(used, 'class Used3 {}')
    setFileText(java, 'class Java { void foo(Used3 used) {} }')
    assertEmpty make()

    assert findClassFile('Used2') == null
  }

  void testClassLoadingDuringBytecodeGeneration() {
    def used = myFixture.addFileToProject('Used.groovy', 'class Used { }')
    def java = myFixture.addFileToProject('Java.java', '''
abstract class Java {
  Object getProp() { return null; }
  abstract void foo(Used used);
}''')
    def main = myFixture.addFileToProject('Main.groovy', '''
class Main {
  def foo(Java j) {
    return j.prop
  }
}''').virtualFile

    assertEmpty make()

    touch(used.virtualFile)
    touch(main)
    assertEmpty make()
  }

  void testMakeInDependentModuleAfterChunkRebuild() {
    def used = myFixture.addFileToProject('Used.groovy', 'class Used { }')
    def java = myFixture.addFileToProject('Java.java', 'class Java { void foo(Used used) {} }')
    def main = myFixture.addFileToProject('Main.groovy', 'class Main extends Java {  }').virtualFile

    addGroovyLibrary(addDependentModule())

    def dep = myFixture.addFileToProject("dependent/Dep.java", "class Dep { }")

    assertEmpty make()

    setFileText(used, 'class Used { String prop }')
    touch(main)
    setFileText(dep, 'class Dep { String prop = new Used().getProp(); }')

    assertEmpty make()
  }

  void "test extend package-private class from another module"() {
    addGroovyLibrary(addDependentModule())

    myFixture.addClass("package foo; class Foo {}")
    myFixture.addFileToProject("dependent/foo/Bar.java", "package foo; class Bar extends Foo {}")
    myFixture.addFileToProject("dependent/foo/Goo.groovy", "package foo; class Goo extends Bar {}")

    assertEmpty make()
  }

  void "test do not recompile unrelated files after breaking compilation"() {
    def fooFile = myFixture.addFileToProject("Foo.groovy", "class Foo {}")
    myFixture.addFileToProject("Bar.groovy", "class Bar {}")
    assertEmpty make()

    def barCompiled = VfsUtil.virtualToIoFile(findClassFile('Bar'))
    def barStamp = barCompiled.lastModified()

    setFileText(fooFile, 'class Foo ext { }')
    shouldFail { make() }
    setFileText(fooFile, 'interface Foo extends Runnable { }')
    assertEmpty make()

    assert barStamp == barCompiled.lastModified()
  }

  void "test module cycle"() {
    def dep = addDependentModule()
    ModuleRootModificationUtil.addDependency(myModule, dep)
    addGroovyLibrary(dep)

    myFixture.addFileToProject('Foo.groovy', 'class Foo extends Bar { static void main(String[] args) { println "Hello from Foo" } }')
    myFixture.addFileToProject('FooX.java', 'class FooX extends Bar { }')
    myFixture.addFileToProject('FooY.groovy', 'class FooY extends BarX { }')
    myFixture.addFileToProject("dependent/Bar.groovy", "class Bar { Foo f; static void main(String[] args) { println 'Hello from Bar' } }")
    myFixture.addFileToProject("dependent/BarX.java", "class BarX { Foo f; }")
    myFixture.addFileToProject("dependent/BarY.groovy", "class BarY extends FooX { }")

    def checkClassFiles = {
      assert findClassFile('Foo', myModule)
      assert findClassFile('FooX', myModule)
      assert findClassFile('Bar', dep)
      assert findClassFile('BarX', dep)

      assert !findClassFile('Bar', myModule)
      assert !findClassFile('BarX', myModule)
      assert !findClassFile('Foo', dep)
      assert !findClassFile('FooX', dep)
    }

    assertEmpty(make())
    checkClassFiles()

    assertEmpty(make())
    checkClassFiles()

    assertOutput('Foo', 'Hello from Foo', myModule)
    assertOutput('Bar', 'Hello from Bar', dep)

    checkClassFiles()
  }

  void testCompileTimeConstants() {
    myFixture.addFileToProject 'Gr.groovy', '''
interface Gr {
  String HELLO = "Hello"
  int MAGIC = 239
  Boolean BOOL = true
  boolean bool = true
}'''
    myFixture.addFileToProject 'Main.java', '''
public class Main {
  public static void main(String[] args) {
    System.out.println(Gr.HELLO + ", " + Gr.BOOL + Gr.bool + Gr.MAGIC);
  }
}
'''
    make()
    assertOutput 'Main', 'Hello, truetrue239'
  }

  void "test reporting rebuild errors caused by missing files excluded from compilation"() {
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo {}')
    myFixture.addFileToProject 'Bar.groovy', 'class Bar extends Foo {}'

    make()

    excludeFromCompilation(foo)

    shouldFail { rebuild() }
  }

  void "test compile groovy excluded from stub generation"() {
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo {}')
    myFixture.addFileToProject 'Bar.groovy', 'class Bar extends Foo {}'

    excludeFromCompilation(GroovyCompilerConfiguration.getInstance(project).excludeFromStubGeneration, foo)

    assertEmpty make()
  }

  private void excludeFromCompilation(PsiFile foo) {
    excludeFromCompilation(CompilerConfiguration.getInstance(project).getExcludedEntriesConfiguration(), foo)
  }

  private excludeFromCompilation(ExcludesConfiguration configuration, PsiFile foo) {
    configuration.addExcludeEntryDescription(new ExcludeEntryDescription(foo.virtualFile, false, true, myFixture.testRootDisposable))
  }

  void "test make stub-level error and correct it"() {
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo { }')
    myFixture.addFileToProject('Bar.java', 'class Bar extends Foo {}')

    assertEmpty make()

    setFileText(foo, 'class Foo implements Runnabl {}')

    def compilerTempRoot = BuildManager.instance.getProjectSystemDirectory(project).absolutePath
    try {
      VfsRootAccess.allowRootAccess(compilerTempRoot) //because compilation error points to file under 'groovyStubs' directory
      shouldFail { make() }
    }
    finally {
      VfsRootAccess.disallowRootAccess(compilerTempRoot)
    }

    setFileText(foo, 'class Foo {}')

    assertEmpty make()
  }

  void "test reporting module compile errors caused by missing files excluded from compilation"() {
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo {}')
    myFixture.addFileToProject('Bar.groovy', 'class Bar extends Foo {}')

    make()

    excludeFromCompilation(foo)

    shouldFail { compileModule(myModule) }
  }

  void "test stubs generated while processing groovy class file dependencies"() {
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo { }')
    def bar = myFixture.addFileToProject('Bar.groovy', 'class Bar extends Foo { }')
    def client = myFixture.addFileToProject('Client.groovy', 'class Client { Bar bar = new Bar() }')
    def java = myFixture.addFileToProject('Java.java', 'class Java extends Client { String getName(Bar bar) { return bar.toString();  } }')

    assertEmpty(make())

    setFileText(bar, 'class Bar { }')

    assertEmpty(make())
    assert findClassFile("Client")
  }

  void "test ignore groovy internal non-existent interface helper inner class"() {
    myFixture.addFileToProject 'Foo.groovy', '''
interface Foo {}

class Zoo {
  Foo foo() {}
  static class Inner implements Foo {}
}

'''
    def bar = myFixture.addFileToProject('Bar.groovy', 'class Bar { def foo = new Zoo.Inner() {}  }')

    assertEmpty make()
    assertEmpty compileFiles(bar.virtualFile)
  }

  void "test multiline strings"() {
    myFixture.addFileToProject 'Foo.groovy', '''class Foo {
  public static final String s = """
multi
line
string
"""
 } '''
    myFixture.addFileToProject 'Bar.java', 'class Bar extends Foo {} '

    assertEmpty make()
  }

  void "test inner java class references with incremental recompilation"() {
    def bar1 = myFixture.addFileToProject('bar/Bar1.groovy', 'package bar; class Bar1 extends Bar2 { } ')
    myFixture.addFileToProject('bar/Bar2.java', 'package bar; class Bar2 extends Bar3 { } ')
    def bar3 = myFixture.addFileToProject('bar/Bar3.groovy', 'package bar; class Bar3 { Bar1 property } ')

    myFixture.addClass("package foo; public class Outer { public static class Inner extends bar.Bar1 { } }")
    def using = myFixture.addFileToProject('UsingInner.groovy', 'import foo.Outer; class UsingInner extends bar.Bar1 { Outer.Inner property } ')

    assertEmpty make()

    touch bar1.virtualFile
    touch bar3.virtualFile
    touch using.virtualFile

    assertEmpty make()
  }

  void "test rename class to java and touch its usage"() {
    def usage = myFixture.addFileToProject('Usage.groovy', 'class Usage { Renamed r } ')
    def renamed = myFixture.addFileToProject('Renamed.groovy', 'public class Renamed { } ')
    assertEmpty make()

    touch usage.virtualFile
    setFileName(renamed, 'Renamed.java')
    assertEmpty make()
  }

  void "test compiling static extension"() {
    setupTestSources()
    myFixture.addFileToProject "src/extension/Extension.groovy", """
package extension
import groovy.transform.CompileStatic

@CompileStatic class Extension {
    static <T> T test2(List<T> self) {
        self.first()
    }
}"""
    myFixture.addFileToProject "src/META-INF/services/org.codehaus.groovy.runtime.ExtensionModule", """
moduleName=extension-verify
moduleVersion=1.0-test
extensionClasses=extension.Extension
staticExtensionClasses=
"""
    myFixture.addFileToProject "tests/AppTest.groovy", """
class AppTest {
    @groovy.transform.CompileStatic
    static main(args) {
        List<String> list = new ArrayList<>()
        list.add("b")
        list.add("c")
        println list.test2()
    }
}
"""
    assertEmpty make()
    assertOutput 'AppTest', 'b'
  }

  void "test no groovy library"() {
    myFixture.addFileToProject("dependent/a.groovy", "")
    addModule("dependent", true)

    def messages = make()
    assert messages.find { it.message.contains("Cannot compile Groovy files: no Groovy library is defined for module 'dependent'") }
  }

  void testGroovyOutputIsInstrumented() {
    myFixture.addFileToProject("Bar.groovy",
       "import org.jetbrains.annotations.NotNull; " +
       "public class Bar {" +
         "void xxx(@NotNull String param) { println param }\n" +
         "static void main(String[] args) { new Bar().xxx(null) }"+
       "}"
    )

    File annotations = new File(PathManager.getJarPathForClass(NotNull.class))
    PsiTestUtil.addLibrary(myModule, "annotations", annotations.getParent(), annotations.getName())

    assertEmpty(make())

    final Ref<Boolean> exceptionFound = Ref.create(Boolean.FALSE)
    ProcessHandler process = runProcess("Bar", myModule, DefaultRunExecutor.class, new ProcessAdapter() {
      @Override
       void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
        println "stdout: " + event.text
        if (ProcessOutputTypes.SYSTEM != outputType) {
          if (!exceptionFound.get()) {
            exceptionFound.set(event.getText().contains("java.lang.IllegalArgumentException: Argument for @NotNull parameter 'param' of Bar.xxx must not be null"))
          }
        }
      }
    }, ProgramRunner.PROGRAM_RUNNER_EP.findExtension(DefaultJavaProgramRunner.class))
    process.waitFor()

    assertTrue(exceptionFound.get())
  }

  void "test extend groovy classes with additional dependencies"() {
    def anotherModule = addModule("another", true)
    addGroovyLibrary(anotherModule)

    PsiTestUtil.addLibrary(myModule, "junit", GroovyFacetUtil.libDirectory, "junit.jar")

    def cliPath = FileUtil.toCanonicalPath(PluginPathManager.getPluginHomePath("groovy") + "/../../build/lib")
    PsiTestUtil.addLibrary(myModule, "cli", cliPath, "commons-cli-1.2.jar")
    PsiTestUtil.addLibrary(anotherModule, "cli", cliPath, "commons-cli-1.2.jar")

    myFixture.addFileToProject("a.groovy", "class Foo extends GroovyTestCase {}")
    myFixture.addFileToProject("b.groovy", "class Bar extends CliBuilder {}")

    myFixture.addFileToProject("another/b.groovy", "class AnotherBar extends CliBuilder {}")

    assertEmpty(make())
  }

  void "test java depends on stub whose generation failed"() {
    Closure<Runnable> createFiles = { String prefix ->
      def genParam = myFixture.addFileToProject(prefix + "GenParam.java", "class GenParam {}")
      myFixture.addFileToProject(prefix + "Intf.java", "class Intf<T extends GenParam> {}")
      myFixture.addFileToProject(prefix + "SuperFoo.java", "class SuperFoo extends Intf<GenParam> {}")
      def fooGroovy = myFixture.addFileToProject(prefix + "Foo.groovy", "class Foo extends SuperFoo {}")
      return {
        touch(genParam.virtualFile)
        touch(fooGroovy.virtualFile)
        myFixture.addFileToProject(prefix + "Bar.java", "class Bar extends Foo { }")
      } as Runnable
    }

    addGroovyLibrary(addModule('mod2', true))

    def touch1 = createFiles('')
    def touch2 = createFiles('mod2/')

    assertEmpty(make())

    touch1.run()
    touch2.run()

    assert !make().find { it.category == CompilerMessageCategory.ERROR }
  }

  void "test report real compilation errors"() {
    addModule('another', true)

    myFixture.addClass('class Foo {}');
    myFixture.addFileToProject('a.groovy', 'import goo.Goo; class Bar { }')
    shouldFail { compileModule(myModule) }
  }

  static class GroovycTest extends GroovyCompilerTest {
    void "test navigate from stub to source"() {
      GroovyFile groovyFile = (GroovyFile) myFixture.addFileToProject("a.groovy", "class Groovy3 { InvalidType type }")
      myFixture.addClass("class Java4 extends Groovy3 {}").containingFile

      def msg = make().find { it.message.contains('InvalidType') }
      assert msg?.virtualFile
      ApplicationManager.application.runWriteAction { msg.virtualFile.delete(this) }

      def messages = make()
      assert messages
      def error = messages.find { it.message.contains('InvalidType') }
      assert error?.virtualFile
      assert groovyFile.classes[0] == GroovyStubNotificationProvider.findClassByStub(project, error.virtualFile)
    }

    void "test config script"() {
      def script = FileUtil.createTempFile("configScriptTest", ".groovy", true)
      FileUtil.writeToFile(script, "import groovy.transform.*; withConfig(configuration) { ast(CompileStatic) }")

      GroovyCompilerConfiguration.getInstance(project).configScript = script.path

      myFixture.addFileToProject("a.groovy", "class A { int s = 'foo' }")
      shouldFail { make() }
    }

    void "test user-level diagnostic for missing dependency of groovy-all"() {
      myFixture.addFileToProject 'Bar.groovy', '''import groovy.util.logging.Commons
@Commons
class Bar {}'''
      def msg = assertOneElement(make())
      assert msg.message.contains('Please')
      assert msg.message.contains('org.apache.commons.logging.Log')
    }

  }

  static class EclipseTest extends GroovyCompilerTest {
    @Override
    protected void setUp() {
      super.setUp()

      ((CompilerConfigurationImpl)CompilerConfiguration.getInstance(project)).defaultCompiler = new GreclipseIdeaCompiler(project)

      def jarName = "groovy-eclipse-batch-2.3.4-01.jar"
      def jarPath = FileUtil.toCanonicalPath(PluginPathManager.getPluginHomePath("groovy") + "/lib/" + jarName)

      GreclipseIdeaCompilerSettings.getSettings(project).greclipsePath = jarPath
    }
  }
}