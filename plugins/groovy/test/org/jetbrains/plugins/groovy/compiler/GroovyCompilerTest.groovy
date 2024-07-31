// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler

import com.intellij.compiler.CompilerConfiguration
import com.intellij.compiler.ParallelCompilationOption
import com.intellij.compiler.server.BuildManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.impl.DefaultJavaProgramRunner
import com.intellij.execution.process.ProcessAdapter
import com.intellij.execution.process.ProcessEvent
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.process.ProcessOutputTypes
import com.intellij.execution.runners.ProgramRunner
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.compiler.CompilerMessageCategory
import com.intellij.openapi.compiler.options.ExcludeEntryDescription
import com.intellij.openapi.compiler.options.ExcludesConfiguration
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.Ref
import com.intellij.openapi.util.Trinity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.pom.java.LanguageLevel
import com.intellij.project.IntelliJProjectConfiguration
import com.intellij.psi.PsiFile
import com.intellij.testFramework.IdeaTestUtil
import com.intellij.testFramework.PsiTestUtil
import com.intellij.testFramework.TestLoggerFactory
import com.intellij.util.ThrowableRunnable
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile
import org.jetbrains.org.objectweb.asm.ClassReader
import org.jetbrains.org.objectweb.asm.ClassVisitor
import org.jetbrains.org.objectweb.asm.Opcodes
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors
import org.jetbrains.plugins.groovy.RepositoryTestLibrary
import org.jetbrains.plugins.groovy.TestLibrary

import java.nio.file.Files

@CompileStatic
abstract class GroovyCompilerTest extends GroovyCompilerTestCase {
  @Override
  protected boolean runInDispatchThread() {
    return false
  }

  @Override protected void setUp() {
    Files.deleteIfExists(TestLoggerFactory.testLogDir.resolve("../log/build-log/build.log"))
    super.setUp()
    Logger.getInstance(GroovyCompilerTest.class).info(testStartMessage)
    addGroovyLibrary(module)
  }

  protected abstract TestLibrary getGroovyLibrary()

  @Override
  protected final void addGroovyLibrary(Module to) {
    groovyLibrary.addTo(to)
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

  protected boolean isRebuildExpectedAfterChangeInJavaClassExtendedByGroovy() {
    return false
  }
  
  void testTransitiveJavaDependencyThroughGroovy() {
    def iFoo = myFixture.addClass "public class IFoo { void foo() {} }" containingFile
    myFixture.addFileToProject "Foo.groovy", '''\
class Foo {
  static IFoo f
  public int foo() { return 239; }
}
'''
    def bar = myFixture.addFileToProject "Bar.groovy", '''
class Bar extends Foo {
  public static void main(String[] args) {
    System.out.println(new Foo().foo());
  }
}
'''
    assertEmpty(make())

    touch(iFoo.virtualFile)
    touch(bar.virtualFile)
    
    // in 2.4:
    // - the Foo is not well-formed (IFoo doesn't exist), so we throw NCDFE;
    // - NCDFE forces loading Foo class node from Foo.groovy file;
    // - Foo.groovy is added to the current compile session;
    // => no chunk rebuild
    //
    // in 2.5:
    // - the Foo is loaded as decompiled node;
    // - when the Bar stub is being written on the disk, it throws NCDFE when trying to resolve IFoo;
    // => chunk rebuild
    //see also org.codehaus.groovy.control.ClassNodeResolver#tryAsLoaderClassOrScript
    if (isRebuildExpectedAfterChangeInJavaClassExtendedByGroovy()) {
      assert make().collect { it.message } == chunkRebuildMessage('Groovy stub generator')
    }
    else {
      assertEmpty make()
    }
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

  void testTransitiveDependencyViaAnnotation() {
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo {}')
    myFixture.addFileToProject('Bar.groovy', 'class Bar { Bar plugin(@DelegatesTo(Foo) c) {} }')
    def goo = myFixture.addFileToProject('Goo.groovy', '@groovy.transform.CompileStatic class Goo { def x(Bar bar) { bar.plugin {} } }')
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

  String getTestStartMessage() { "Starting " + getClass().name + " " + getName() }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    try {
      super.runTestRunnable(testRunnable)
    }
    catch (Throwable e) {
      printLogs()
      throw e
    }
  }

  private void printLogs() {
    println "Idea log"
    TestLoggerFactory.dumpLogToStdout(getTestStartMessage())

    def makeLog = TestLoggerFactory.testLogDir.resolve("../log/build-log/build.log")
    if (Files.exists(makeLog)) {
      println "\n\nServer Log:"
      println Files.readString(makeLog)
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

    if(AdvancedSettings.getBoolean("compiler.unified.ic.implementation")) {
      long oldFooStamp = findClassFile("Foo").lastModified()
      long oldGooStamp = findClassFile("Goo").lastModified()
      long oldMainStamp = findClassFile("Main").lastModified()

      touch(foo.virtualFile)
      touch(main.containingFile.virtualFile)
      shouldSucceed { make() }

      assert oldFooStamp != findClassFile("Foo").lastModified()
      assert oldMainStamp != findClassFile("Main").lastModified()
      assert oldGooStamp != findClassFile("Goo").lastModified()
    } else {
      touch(foo.virtualFile)
      touch(main.containingFile.virtualFile)
      if (isRebuildExpectedAfterChangeInJavaClassExtendedByGroovy()) {
        assert make().collect { it.message } == chunkRebuildMessage('Groovy stub generator')
      }
      else {
        assertEmpty(make())
      }
    }
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
    ModuleRootModificationUtil.addDependency module, dep2

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

  void "test with annotation processing enabled"() {
    def profile = (ProcessorConfigProfile)CompilerConfiguration.getInstance(project).getAnnotationProcessingConfiguration(module)
    profile.enabled = true
    profile.obtainProcessorsFromClasspath = true

    myFixture.addFileToProject 'Foo.groovy', 'class Foo {}'

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
    long oldBaseStamp = findClassFile("Base").lastModified()
    long oldMainStamp = findClassFile("Main").lastModified()

    touch(main)
    touch(foo)
    assertEmpty make()
    assert oldMainStamp != findClassFile("Main").lastModified()
    assert oldBaseStamp == findClassFile("Base").lastModified()
  }

  void 'test changed groovy refers to java which refers to changed groovy and fails in stub generator'() {
    def used = myFixture.addFileToProject('Used.groovy', 'class Used { }')
    def java = myFixture.addFileToProject('Java.java', 'class Java { void foo(Used used) {} }')
    def main = myFixture.addFileToProject('Main.groovy', 'class Main extends Java {  }').virtualFile
    assertEmpty make()

    touch(used.virtualFile)
    touch(main)
    if (isRebuildExpectedAfterChangesInGroovyWhichUseJava()) {
      assert make().collect { it.message } == chunkRebuildMessage('Groovy stub generator')
    }
    else {
      assertEmpty make()
    }

    setFileText(used, 'class Used2 {}')
    shouldFail { make() }
    assert findClassFile('Used') == null

    setFileText(used, 'class Used3 {}')
    setFileText(java, 'class Java { void foo(Used3 used) {} }')
    assertEmpty make()

    assert findClassFile('Used2') == null
  }

  protected abstract List<String> chunkRebuildMessage(String builder)

  void 'test changed groovy refers to java which refers to changed groovy and fails in compiler'() {
    def used = myFixture.addFileToProject('Used.groovy', 'class Used { }')
    myFixture.addFileToProject('Java.java', '''
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
    def messages = make()
    if (isRebuildExpectedAfterChangesInGroovyWhichUseJava()) {
      assert messages.collect { it.message } == chunkRebuildMessage("Groovy compiler")
    }
    else {
      assertEmpty messages
    }
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

    if (isRebuildExpectedAfterChangesInGroovyWhichUseJava()) {
      assert make().collect { it.message } == chunkRebuildMessage('Groovy stub generator')
    }
    else {
      assertEmpty make()
    }
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

    def barCompiled = findClassFile('Bar')
    def barStamp = barCompiled.lastModified()

    setFileText(fooFile, 'class Foo ext { }')
    shouldFail { make() }
    setFileText(fooFile, 'interface Foo extends Runnable { }')
    assertEmpty make()

    assert barStamp == barCompiled.lastModified()
  }

  void "test module cycle"() {
    def dep = addDependentModule()
    ModuleRootModificationUtil.addDependency(module, dep)
    addGroovyLibrary(dep)

    myFixture.addFileToProject('Foo.groovy', 'class Foo extends Bar { static void main(String[] args) { println "Hello from Foo" } }')
    myFixture.addFileToProject('FooX.java', 'class FooX extends Bar { }')
    myFixture.addFileToProject('FooY.groovy', 'class FooY extends BarX { }')
    myFixture.addFileToProject("dependent/Bar.groovy", "class Bar { Foo f; static void main(String[] args) { println 'Hello from Bar' } }")
    myFixture.addFileToProject("dependent/BarX.java", "class BarX { Foo f; }")
    myFixture.addFileToProject("dependent/BarY.groovy", "class BarY extends FooX { }")

    def checkClassFiles = {
      assert findClassFile('Foo', module)
      assert findClassFile('FooX', module)
      assert findClassFile('Bar', dep)
      assert findClassFile('BarX', dep)

      assert !findClassFile('Bar', module)
      assert !findClassFile('BarX', module)
      assert !findClassFile('Foo', dep)
      assert !findClassFile('FooX', dep)
    }

    assertEmpty(make())
    checkClassFiles()

    assertEmpty(make())
    checkClassFiles()

    assertOutput('Foo', 'Hello from Foo', module)
    assertOutput('Bar', 'Hello from Bar', dep)

    checkClassFiles()
  }

  void testCompileTimeConstants() {
    myFixture.addFileToProject 'Gr.groovy', '''
class Gr {
  public static final String HELLO = "Hello"
  public static final int MAGIC = 239
  public static final Boolean BOOL = true
  public static final boolean bool = true
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
    VfsRootAccess.allowRootAccess(getTestRootDisposable(), compilerTempRoot) //because compilation error points to file under 'groovyStubs' directory
    shouldFail { make() }

    setFileText(foo, 'class Foo {}')

    assertEmpty make()
  }

  void "test reporting module compile errors caused by missing files excluded from compilation"() {
    def foo = myFixture.addFileToProject('Foo.groovy', 'class Foo {}')
    myFixture.addFileToProject('Bar.groovy', 'class Bar extends Foo {}')

    make()

    excludeFromCompilation(foo)

    shouldFail { compileModule(module) }
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

  protected boolean isRebuildExpectedAfterChangesInGroovyWhichUseJava() {
    return true;
  }

  void "test inner java class references with incremental recompilation"() {
    def bar1 = myFixture.addFileToProject('bar/Bar1.groovy', 'package bar; class Bar1 extends Bar2 { } ')
    myFixture.addFileToProject('bar/Bar2.java', 'package bar; class Bar2 extends Bar3 { } ')
    def bar3 = myFixture.addFileToProject('bar/Bar3.groovy', 'package bar; class Bar3 { Bar1 property } ')

    myFixture.addClass("package foo; public class Outer { public static class Inner extends bar.Bar1 { } }")
    def using = myFixture.addFileToProject('UsingInner.groovy',
                                           'import foo.Outer; class UsingInner extends bar.Bar1 { Outer.Inner property } ')

    assertEmpty make()

    if(AdvancedSettings.getBoolean("compiler.unified.ic.implementation")) {
      long oldBar1Stamp = findClassFile("bar/Bar1").lastModified()
      long oldBar2Stamp = findClassFile("bar/Bar2").lastModified()
      long oldBar3Stamp = findClassFile("bar/Bar3").lastModified()

      touch(bar1.virtualFile)
      touch(bar3.virtualFile)
      shouldSucceed { make() }

      assert oldBar1Stamp != findClassFile("bar/Bar1").lastModified()
      assert oldBar2Stamp != findClassFile("bar/Bar2").lastModified()
      assert oldBar3Stamp != findClassFile("bar/Bar3").lastModified()
    } else {
      touch bar1.virtualFile
      touch bar3.virtualFile
      touch using.virtualFile

      if (isRebuildExpectedAfterChangesInGroovyWhichUseJava()) {
        assert make().collect { it.message } == chunkRebuildMessage('Groovy compiler')
      }
      else {
        assertEmpty make()
      }
    }
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
    PsiTestUtil.addLibrary(module, "annotations", annotations.getParent(), annotations.getName())

    assertEmpty(make())

    final Ref<Boolean> exceptionFound = Ref.create(Boolean.FALSE)
    ProcessHandler process = runProcess("Bar", module, DefaultRunExecutor.class, new ProcessAdapter() {
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
    PsiTestUtil.addProjectLibrary(module, "junit", IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("JUnit3"))
    def library = getGroovyLibrary()
    def coordinate = (library as RepositoryTestLibrary).coordinates.first()
    if (!coordinate.contains(":groovy-all:")) {
      def testLibrary = new RepositoryTestLibrary(coordinate.replace(":groovy:", ":groovy-test:"))
      testLibrary.addTo(module)
    }
    if (coordinate.contains(":2.")) {
      myFixture.addFileToProject("a.groovy", "class Foo extends groovy.util.GroovyTestCase {}")
    }
    else {
      myFixture.addFileToProject("a.groovy", "class Foo extends groovy.test.GroovyTestCase {}")
    }
    assertEmpty(make())
  }

  void "test java depends on stub whose generation failed"() {
    CompilerConfiguration.getInstance(project).parallelCompilationOption = ParallelCompilationOption.DISABLED
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

  void "test recompile one file that triggers chunk rebuild inside"() {
    'do test recompile one file that triggers chunk rebuild inside'(this instanceof GroovycTestBase)
  }

  protected final void 'do test recompile one file that triggers chunk rebuild inside'(boolean expectRebuild) {
    myFixture.addFileToProject('BuildContext.groovy', '''
@groovy.transform.CompileStatic 
class BuildContext {
  static BuildContext createContext(PropTools tools) { return BuildContextImpl.create(tools) } 
}

''')
    myFixture.addFileToProject('PropTools.groovy', 'class PropTools { SomeTool someTool }')
    myFixture.addFileToProject('SomeTool.groovy', 'interface SomeTool { void call(BuildContext ctx) }')
    def subText = '''
@groovy.transform.CompileStatic 
class BuildContextImpl extends BuildContext {
  static BuildContextImpl create(PropTools tools) { return new BuildContextImpl() }
  void foo(SomeTool tool) { tool.call(this) } 
}
'''
    def sub = myFixture.addFileToProject('BuildContextImpl.groovy', subText)
    assertEmpty(make())

    setFileText(sub, subText + ' ')
    def makeMessages = make()
    def fileMessages = compileFiles(sub.virtualFile)
    if (expectRebuild) {
      assert makeMessages.collect { it.message } == chunkRebuildMessage('Groovy compiler')
      assert fileMessages.collect { it.message == 'Consider building whole project or rebuilding the module' }
    }
    else {
      assertEmpty makeMessages
      assertEmpty fileMessages
    }
  }

  void "test report real compilation errors"() {
    addModule('another', true)

    myFixture.addClass('class Foo {}')
    myFixture.addFileToProject('a.groovy', 'import goo.Goo; class Bar { }')
    shouldFail { compileModule(module) }
  }

  void "test honor bytecode version"() {
    Trinity<LanguageLevel, String, Integer> base
    Trinity<LanguageLevel, String, Integer> old
    if (getGroovyLibrary() == GroovyProjectDescriptors.LIB_GROOVY_2_4) {
      base = [LanguageLevel.JDK_1_8, '1.8', Opcodes.V1_8]
      old = [LanguageLevel.JDK_1_6, '1.6', Opcodes.V1_6]
    }
    else {
      base = [LanguageLevel.JDK_11, '11', Opcodes.V11]
      old = [LanguageLevel.JDK_1_8, '1.8', Opcodes.V1_8]
    }
    IdeaTestUtil.setModuleLanguageLevel(module, base.first)
    CompilerConfiguration.getInstance(project).setBytecodeTargetLevel(module, base.second)

    myFixture.addFileToProject('a.groovy', 'class Foo { }')
    assertEmpty make()
    assert getClassFileVersion('Foo') == base.third

    IdeaTestUtil.setModuleLanguageLevel(module, old.first)
    CompilerConfiguration.getInstance(project).setBytecodeTargetLevel(module, old.second)
    assertEmpty rebuild()
    assert getClassFileVersion('Foo') == old.third
  }

  private int getClassFileVersion(String className) {
    def classFile = findClassFile(className)
    int version = -1
    new ClassReader(FileUtil.loadFileBytes(classFile)).accept(new ClassVisitor(Opcodes.ASM9) {
      @Override
      void visit(int v, int access, String name, String signature, String superName, String[] interfaces) {
        version = v
      }
    }, 0)
    return version
  }

  void "test using trait from java"() {
    myFixture.addFileToProject('a.groovy', 'trait Foo { }')
    myFixture.addFileToProject('b.java', 'class Bar implements Foo { Foo f; }')
    assertEmpty(make())

    CompilerConfiguration.getInstance(project).buildProcessVMOptions += " -D$JpsGroovycRunner.GROOVYC_IN_PROCESS=false"
    assertEmpty(rebuild())
  }
}
