// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.compiler.CompilerConfiguration;
import com.intellij.compiler.ParallelCompilationOption;
import com.intellij.compiler.server.BuildManager;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.execution.impl.DefaultJavaProgramRunner;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.execution.runners.ProgramRunner;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.compiler.CompilerMessage;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.compiler.options.ExcludeEntryDescription;
import com.intellij.openapi.compiler.options.ExcludesConfiguration;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.options.advanced.AdvancedSettings;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.project.IntelliJProjectConfiguration;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.util.ThrowableRunnable;
import com.intellij.util.containers.ContainerUtil;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.incremental.groovy.JpsGroovycRunner;
import org.jetbrains.jps.model.java.compiler.ProcessorConfigProfile;
import org.jetbrains.org.objectweb.asm.ClassReader;
import org.jetbrains.org.objectweb.asm.ClassVisitor;
import org.jetbrains.org.objectweb.asm.Opcodes;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.RepositoryTestLibrary;
import org.jetbrains.plugins.groovy.TestLibrary;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Function;

public abstract class GroovyCompilerTest extends GroovyCompilerTestCase {
  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    Files.deleteIfExists(TestLoggerFactory.getTestLogDir().resolve("../log/build-log/build.log"));
    Logger.getInstance(GroovyCompilerTest.class).info(getTestStartMessage());
    addGroovyLibrary(getModule());
  }

  protected abstract TestLibrary getGroovyLibrary();

  @Override
  protected final void addGroovyLibrary(Module to) {
    getGroovyLibrary().addTo(to);
  }

  public void testPlainGroovy() throws Throwable {
    myFixture.addFileToProject("A.groovy", "println '239'");
    assert make().isEmpty();
    assertOutput("A", "239");
  }

  public void testJavaDependsOnGroovy() throws Throwable {
    myFixture.addClass(
      "public class Foo {" + "public static void main(String[] args) { " + "  System.out.println(new Bar().foo());" + "}" + "}");
    myFixture.addFileToProject("Bar.groovy", "class Bar {" + "  def foo() {" + "    239" + "  }" + "}");
    make();
    assertOutput("Foo", "239");
  }

  public void testCorrectFailAndCorrect() throws Exception {
    myFixture.addClass(
      "public class Foo {" + "public static void main(String[] args) { " + "  System.out.println(new Bar().foo());" + "}" + "}");
    final String barText = "class Bar {" + "  def foo() { 239  }" + "}";
    final PsiFile file = myFixture.addFileToProject("Bar.groovy", barText);
    make();
    assertOutput("Foo", "239");

    setFileText(file, "class Bar {}");
    GroovyCompilerTestCase.shouldFail(make());

    setFileText(file, barText);
    make();
    assertOutput("Foo", "239");
  }

  public void testRenameToJava() throws Throwable {
    myFixture.addClass(
      "public class Foo {" + "public static void main(String[] args) { " + "  System.out.println(new Bar().foo());" + "}" + "}");

    final PsiFile bar =
      myFixture.addFileToProject("Bar.groovy", "public class Bar {" + "public int foo() { " + "  return 239;" + "}" + "}");

    make();
    assertOutput("Foo", "239");

    setFileName(bar, "Bar.java");

    make();
    assertOutput("Foo", "239");
  }

  public void testTransitiveJavaDependency() throws Throwable {
    final VirtualFile ifoo = myFixture.addClass("public interface IFoo { int foo(); }").getContainingFile().getVirtualFile();
    myFixture.addClass("public class Foo implements IFoo {" + "  public int foo() { return 239; }" + "}");
    final PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar {" +
                                                                 "Foo foo\n" +
                                                                 "public static void main(String[] args) { " +
                                                                 "  System.out.println(new Foo().foo());" +
                                                                 "}" +
                                                                 "}");
    assert make().isEmpty();
    assertOutput("Bar", "239");

    touch(ifoo);
    touch(bar.getVirtualFile());

    //assertTrue(assertOneElement(make()).contains("WARNING: Groovyc stub generation failed"));
    assert make().isEmpty();
    assertOutput("Bar", "239");
  }

  protected boolean isRebuildExpectedAfterChangeInJavaClassExtendedByGroovy() {
    return false;
  }

  public void testTransitiveJavaDependencyThroughGroovy() throws IOException {
    PsiFile iFoo = myFixture.addClass("public class IFoo { void foo() {} }").getContainingFile();
    myFixture.addFileToProject("Foo.groovy", """
      class Foo {
        static IFoo f
        public int foo() { return 239; }
      }
    """);
    PsiFile bar = myFixture.addFileToProject("Bar.groovy", """
      class Bar extends Foo {
        public static void main(String[] args) {
          System.out.println(new Foo().foo());
        }
      }
    """);
    assert make().isEmpty();

    touch(iFoo.getVirtualFile());
    touch(bar.getVirtualFile());

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
      assert ContainerUtil.map(make(), m -> m.getMessage()).equals(chunkRebuildMessage("Groovy stub generator"));
    } else {
      assert make().isEmpty();
    }

  }

  public void testTransitiveGroovyDependency() throws Throwable {
    PsiFile foo = myFixture.addFileToProject("Foo.groovy", "class Foo {} ");
    PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar extends Foo {}");
    PsiFile goo = myFixture.addFileToProject("Goo.groovy", "class Goo extends Bar {}");
    assert make().isEmpty();

    touch(foo.getVirtualFile());
    touch(goo.getVirtualFile());
    assert make().isEmpty();
  }

  public void testTransitiveDependencyViaAnnotation() throws IOException {
    PsiFile foo = myFixture.addFileToProject("Foo.groovy", "class Foo {}");
    myFixture.addFileToProject("Bar.groovy", "class Bar { Bar plugin(@DelegatesTo(Foo) c) {} }");
    PsiFile goo = myFixture.addFileToProject("Goo.groovy", "@groovy.transform.CompileStatic class Goo { def x(Bar bar) { bar.plugin {} } }");
    assert make().isEmpty();

    touch(foo.getVirtualFile());
    touch(goo.getVirtualFile());

    assert make().isEmpty();
  }

  public void testJavaDependsOnGroovyEnum() throws Throwable {
    myFixture.addFileToProject("Foo.groovy", "enum Foo { FOO }");
    myFixture.addClass("class Bar { Foo f; }");
    assert make().isEmpty();
  }

  public void testDeleteTransitiveJavaClass() throws Throwable {
    myFixture.addClass("public interface IFoo { int foo(); }");
    myFixture.addClass("public class Foo implements IFoo {" + "  public int foo() { return 239; }" + "}");
    final PsiFile bar = myFixture.addFileToProject("Bar.groovy", """
      class Bar {\
      Foo foo
      public static void main(String[] args) { \
        System.out.println(new Foo().foo());\
      }\
      }""");
    assert make().isEmpty();
    assertOutput("Bar", "239");

    deleteClassFile("IFoo");
    touch(bar.getVirtualFile());

    //assertTrue(assertOneElement(make()).contains("WARNING: Groovyc stub generation failed"));
    assert make().isEmpty();
    assertOutput("Bar", "239");
  }

  public void testGroovyDependsOnGroovy() throws Throwable {
    myFixture.addClass("public class JustToMakeGroovyGenerateStubs {}");
    myFixture.addFileToProject("Foo.groovy", "class Foo { }");
    final PsiFile bar = myFixture.addFileToProject("Bar.groovy", """
      class Bar {\
      def foo(Foo f) {}
      public static void main(String[] args) { \
        System.out.println(239);\
      }\
      }""");
    assert make().isEmpty();
    assertOutput("Bar", "239");

    touch(bar.getVirtualFile());

    assert make().isEmpty();
    assertOutput("Bar", "239");
  }

  public String getTestStartMessage() { return "Starting " + getClass().getName() + " " + getName(); }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    try {
      super.runTestRunnable(testRunnable);
    }
    catch (Throwable e) {
      printLogs();
      throw e;
    }
  }

  private void printLogs() throws IOException {
    DefaultGroovyMethods.println(this, "Idea log");
    TestLoggerFactory.dumpLogToStdout(getTestStartMessage());

    Path makeLog = TestLoggerFactory.getTestLogDir().resolve("../log/build-log/build.log");
    if (Files.exists(makeLog)) {
      DefaultGroovyMethods.println(this, "\n\nServer Log:");
      DefaultGroovyMethods.println(this, Files.readString(makeLog));
    }

    System.out.flush();
  }

  public void testMakeInTests() throws Throwable {
    setupTestSources();
    myFixture.addFileToProject("tests/Super.groovy", "class Super {}");
    assert make().isEmpty();

    myFixture.addFileToProject("tests/Sub.groovy", """
      class Sub {
        Super xxx() {}
        static void main(String[] args) {\
          println 'hello'\
        }\
      }""");

    myFixture.addFileToProject("tests/Java.java", "public class Java {}");

    assert make().isEmpty();
    assertOutput("Sub", "hello");
  }

  public void testTestsDependOnProduction() throws Throwable {
    setupTestSources();
    myFixture.addFileToProject("src/com/Bar.groovy", "package com\nclass Bar {}");
    myFixture.addFileToProject("src/com/ToGenerateStubs.java", "package com;\npublic class ToGenerateStubs {}");
    myFixture.addFileToProject("tests/com/BarTest.groovy", "package com\nclass BarTest extends Bar {}");
    assert make().isEmpty();
  }

  public void testStubForGroovyExtendingJava() throws Exception {
    PsiFile foo = myFixture.addFileToProject("Foo.groovy", "class Foo extends Goo { }");
    myFixture.addFileToProject("Goo.groovy", "class Goo extends Main { void bar() { println 'hello' } }");
    PsiClass main = myFixture.addClass("public class Main { public static void main(String[] args) { new Goo().bar(); } }");
    assert make().isEmpty();

    if (AdvancedSettings.getBoolean("compiler.unified.ic.implementation")) {
      long oldFooStamp = findClassFile("Foo").lastModified();
      long oldGooStamp = findClassFile("Goo").lastModified();
      long oldMainStamp = findClassFile("Main").lastModified();

      touch(foo.getVirtualFile());
      touch(main.getContainingFile().getVirtualFile());
      GroovyCompilerTestCase.shouldSucceed(make());

      assert oldFooStamp != findClassFile("Foo").lastModified();
      assert oldMainStamp != findClassFile("Main").lastModified();
      assert oldGooStamp != findClassFile("Goo").lastModified();
    }
    else {
      touch(foo.getVirtualFile());
      touch(main.getContainingFile().getVirtualFile());
      if (isRebuildExpectedAfterChangeInJavaClassExtendedByGroovy()) {
        assert ContainerUtil.map(make(), it -> it.getMessage()).equals(chunkRebuildMessage("Groovy stub generator"));
      } else {
        assert make().isEmpty();
      }
    }
  }

  public void testDontApplyTransformsFromSameModule() throws Exception {
    addTransform();

    myFixture.addClass("public class JavaClassToGenerateStubs {}");

    assert make().isEmpty();
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
      }
    """);

    myFixture.addFileToProject("Foo.java", """
      public class Foo {
      public static int autoImported() { return 239; }
      }""");

    CompilerConfiguration.getInstance(getProject()).addResourceFilePattern("*.ASTTransformation");

    myFixture.addFileToProject("META-INF/services/org.codehaus.groovy.transform.ASTTransformation", "Transf");
  }

  public void testApplyTransformsFromDependencies() throws Exception {
    addTransform();

    myFixture.addFileToProject("dependent/Bar.groovy", """
      class Bar {
        static Object zzz = autoImported()
        static void main(String[] args) {
          println zzz
        }
      }""");

    myFixture.addFileToProject("dependent/AJavaClass.java", "class AJavaClass {}");

    Module dep = addDependentModule();

    addGroovyLibrary(dep);

    assert make().isEmpty();
    assertOutput("Bar", "239", dep);
  }

  public void testIndirectDependencies() throws Exception {
    myFixture.addFileToProject("dependent1/Bar1.groovy", "class Bar1 {}");
    myFixture.addFileToProject("dependent2/Bar2.groovy", "class Bar2 extends Bar1 {}");
    PsiFile main = myFixture.addFileToProject("Main.groovy", "class Main extends Bar2 {}");

    Module dep1 = addModule("dependent1", true);
    Module dep2 = addModule("dependent2", true);
    ModuleRootModificationUtil.addDependency(dep2, dep1);
    ModuleRootModificationUtil.addDependency(getModule(), dep2);

    addGroovyLibrary(dep1);
    addGroovyLibrary(dep2);

    assert make().isEmpty();

    touch(main.getVirtualFile());
    assert make().isEmpty();
  }

  public void testExtendFromGroovyAbstractClass() {
    myFixture.addFileToProject("Super.groovy", "abstract class Super {}");
    myFixture.addFileToProject("AJava.java", "public class AJava {}");
    assert make().isEmpty();

    myFixture.addFileToProject("Sub.groovy", "class Sub extends Super {}");
    assert make().isEmpty();
  }

  public void test1_7InnerClass() throws Exception {
    myFixture.addFileToProject("Foo.groovy", """
      class Foo {
        static class Bar {}
      }
    """);
    PsiFile javaFile = myFixture.addFileToProject("AJava.java", "public class AJava extends Foo.Bar {}");
    assert make().isEmpty();

    touch(javaFile.getVirtualFile());
    assert make().isEmpty();
  }

public void testRecompileDependentClass() throws Exception {
    PsiFile cloud = myFixture.addFileToProject("Cloud.groovy", """
      class Cloud {
        def accessFooProperty(Foo c) {
          c.prop = 2
        }
      }
    """);
    myFixture.addFileToProject("Foo.groovy", """
      class Foo {
        def withGooParameter(Goo x) {}
      }
    """);
    PsiFile goo = myFixture.addFileToProject("Goo.groovy", "class Goo {}");

    assert make().isEmpty();

    touch(cloud.getVirtualFile());
    touch(goo.getVirtualFile());
    assert make().isEmpty();
  }

public void testRecompileExpressionReferences() throws Exception {
    PsiFile rusCon = myFixture.addFileToProject("RusCon.groovy", """
      interface RusCon {
        Closure foo = { Seq.foo() }
      }
    """);
    myFixture.addFileToProject("Seq.groovy", """
      class Seq implements RusCon {
        static def foo() { }
      }
    """);
    assert make().isEmpty();

    touch(rusCon.getVirtualFile());
    assert make().isEmpty();
  }

public void testRecompileImportedClass() throws Exception {
    PsiFile bar = myFixture.addFileToProject("pack/Bar.groovy", """
      package pack
      import pack.Foo
      class Bar {}
    """);
    myFixture.addFileToProject("pack/Foo.groovy", """
      package pack
      class Foo extends Goo {
      }
    """);
    PsiFile goo = myFixture.addFileToProject("pack/Goo.groovy", """
      package pack
      class Goo {}
    """);

    assert make().isEmpty();

    touch(bar.getVirtualFile());
    touch(goo.getVirtualFile());
    assert make().isEmpty();
  }

public void testRecompileDependentClassesWithOnlyOneChanged() throws Exception {
    PsiFile bar = myFixture.addFileToProject("Bar.groovy", """
      class Bar {
        Foo f
      }
    """);
    myFixture.addFileToProject("Foo.groovy", """
      class Foo extends Bar {
      }
    """);

    assert make().isEmpty();

    touch(bar.getVirtualFile());
    assert make().isEmpty();
  }

  public void testDollarGroovyInnerClassUsagesInStubs() throws Exception {
    PsiClass javaFile = myFixture.addClass("""
      public class JavaClass {
        public static class InnerJavaClass {}
      }
    """);
    myFixture.addFileToProject("WithInner.groovy", """
      class WithInner {
        static class Inner {}
      }
    """);
    assert make().isEmpty();

    myFixture.addFileToProject("Usage.groovy", """
      class Usage {
        def foo(WithInner.Inner i) {}
        def foo(JavaClass.InnerJavaClass i) {}
      }
    """);

    touch(javaFile.getContainingFile().getVirtualFile());
    assert make().isEmpty();
  }

  public void testDollarGroovyInnerClassUsagesInStubs2() throws Exception {
    myFixture.addClass(" public class JavaClass { } ");
    myFixture.addFileToProject("WithInner.groovy", """
      class WithInner {
        static class Inner {}
      }
    """);

    myFixture.addFileToProject("Usage.groovy", """
      class Usage {
        def foo(WithInner.Inner i) {}
      }
    """);
  assert make().isEmpty();
  }

  public void testGroovyAnnotations() {
    myFixture.addClass("public @interface Anno { Class<?>[] value(); }");
    myFixture.addFileToProject("Foo.groovy", "@Anno([String]) class Foo {}");
    myFixture.addFileToProject("Bar.java", "class Bar extends Foo {}");

    assert make().isEmpty();
  }

  public void test_with_annotation_processing_enabled() {
    ProcessorConfigProfile profile = (ProcessorConfigProfile)CompilerConfiguration.getInstance(getProject()).getAnnotationProcessingConfiguration(getModule());
    profile.setEnabled(true);
    profile.setObtainProcessorsFromClasspath(true);

    myFixture.addFileToProject("Foo.groovy", "class Foo {}");

    assert make().isEmpty();
  }

public void testGenericStubs() {
    myFixture.addFileToProject("Foo.groovy", "class Foo { List<String> list }");
    myFixture.addFileToProject("Bar.java", "class Bar {{ for (String s : new Foo().getList()) { s.hashCode(); } }}");
    assert make().isEmpty();
  }

  public void testDuplicateClassDuringCompilation() throws Exception {
    VirtualFile base = myFixture.addFileToProject("p/Base.groovy", "package p; class Base { }").getVirtualFile();
    myFixture.addFileToProject("p/Indirect.groovy", """
      package p
      class Indirect {
        private static class Inner { Base b }
      
        private Indirect.Inner foo(Indirect.Inner g1, Inner g2, Base b) {}
      }
     """).getVirtualFile();
    VirtualFile foo = myFixture.addFileToProject("Foo.groovy", "class Foo { p.Indirect foo() {} }").getVirtualFile();
    assert make().isEmpty();

    touch(foo);
    touch(base);
    assert make().isEmpty();
  }

  public void testDontRecompileUnneeded() throws IOException {
    myFixture.addFileToProject("Base.groovy", "class Base { }");
    VirtualFile foo = myFixture.addFileToProject("Foo.groovy", "class Foo extends Base { }").getVirtualFile();
    myFixture.addFileToProject("Bar.groovy", "class Bar extends Foo { }");
    VirtualFile main = myFixture.addFileToProject("Main.groovy", "class Main extends Bar { }").getVirtualFile();
    assert make().isEmpty();
    long oldBaseStamp = findClassFile("Base").lastModified();
    long oldMainStamp = findClassFile("Main").lastModified();

    touch(main);
    touch(foo);
    assert make().isEmpty();
    assert oldMainStamp != findClassFile("Main").lastModified();
    assert oldBaseStamp == findClassFile("Base").lastModified();
  }

  public void test_changed_groovy_refers_to_java_which_refers_to_changed_groovy_and_fails_in_stub_generator() throws IOException {
    PsiFile used = myFixture.addFileToProject("Used.groovy", "class Used { }");
    PsiFile java = myFixture.addFileToProject("Java.java", "class Java { void foo(Used used) {} }");
    VirtualFile main = myFixture.addFileToProject("Main.groovy", "class Main extends Java {  }").getVirtualFile();
    assert make().isEmpty();

    touch(used.getVirtualFile());
    touch(main);
    if (isRebuildExpectedAfterChangesInGroovyWhichUseJava()) {
      assert ContainerUtil.map(make(), it -> it.getMessage()).equals(chunkRebuildMessage("Groovy stub generator"));
    } else {
      assert make().isEmpty();
    }

    setFileText(used, "class Used2 {}");
    GroovyCompilerTestCase.shouldFail(make());
    assert findClassFile("Used") == null;

    setFileText(used, "class Used3 {}");
    setFileText(java, "class Java { void foo(Used3 used) {} }");
    assert make().isEmpty();

    assert findClassFile("Used2") == null;
  }

  protected abstract List<String> chunkRebuildMessage(String builder);

  public void test_changed_groovy_refers_to_java_which_refers_to_changed_groovy_and_fails_in_compiler() throws IOException {
    PsiFile used = myFixture.addFileToProject("Used.groovy", "class Used { }");
    myFixture.addFileToProject("Java.java", """
      abstract class Java {
        Object getProp() { return null; }
        abstract void foo(Used used);
      }
    """);
    VirtualFile main = myFixture.addFileToProject("Main.groovy", """
      class Main {
        def foo(Java j) {
          return j.prop
        }
      }
    """).getVirtualFile();

    assert make().isEmpty();

    touch(used.getVirtualFile());
    touch(main);

    List<CompilerMessage> messages = make();
    if (isRebuildExpectedAfterChangesInGroovyWhichUseJava()){
      assert ContainerUtil.map(messages, it -> it.getMessage()).equals(chunkRebuildMessage("Groovy compiler"));
    } else {
      assert messages.isEmpty();
    }
  }

  public void testMakeInDependentModuleAfterChunkRebuild() throws IOException {
    PsiFile used = myFixture.addFileToProject("Used.groovy", "class Used { }");
    PsiFile java = myFixture.addFileToProject("Java.java", "class Java { void foo(Used used) {} }");
    VirtualFile main = myFixture.addFileToProject("Main.groovy", "class Main extends Java {  }").getVirtualFile();

    addGroovyLibrary(addDependentModule());

    PsiFile dep = myFixture.addFileToProject("dependent/Dep.java", "class Dep { }");

    assert make().isEmpty();

    setFileText(used, "class Used { String prop }");
    touch(main);
    setFileText(dep, "class Dep { String prop = new Used().getProp(); }");

    if (isRebuildExpectedAfterChangesInGroovyWhichUseJava()) {
      assert ContainerUtil.map(make(), it -> it.getMessage()).equals(chunkRebuildMessage("Groovy stub generator"));
    } else {
      assert make().isEmpty();
    }
  }

  public void test_extend_package_private_class_from_another_module() {
    addGroovyLibrary(addDependentModule());

    myFixture.addClass("package foo; class Foo {}");
    myFixture.addFileToProject("dependent/foo/Bar.java", "package foo; class Bar extends Foo {}");
    myFixture.addFileToProject("dependent/foo/Goo.groovy", "package foo; class Goo extends Bar {}");

    assert make().isEmpty();
  }

  public void test_do_not_recompile_unrelated_files_after_breaking_compilation() throws IOException {
    PsiFile fooFile = myFixture.addFileToProject("Foo.groovy", "class Foo {}");
    myFixture.addFileToProject("Bar.groovy", "class Bar {}");
    assert make().isEmpty();

    File barCompiled = findClassFile("Bar");
    long barStamp = barCompiled.lastModified();

    setFileText(fooFile, "class Foo ext { }");
    GroovyCompilerTestCase.shouldFail(make());
    setFileText(fooFile, "interface Foo extends Runnable { }");
    assert make().isEmpty();

    assert barStamp == barCompiled.lastModified();
  }

  public void test_module_cycle() throws ExecutionException {
    final Module dep = addDependentModule();
    ModuleRootModificationUtil.addDependency(getModule(), dep);
    addGroovyLibrary(dep);

    myFixture.addFileToProject("Foo.groovy", "class Foo extends Bar { static void main(String[] args) { println \"Hello from Foo\" } }");
    myFixture.addFileToProject("FooX.java", "class FooX extends Bar { }");
    myFixture.addFileToProject("FooY.groovy", "class FooY extends BarX { }");
    myFixture.addFileToProject("dependent/Bar.groovy", "class Bar { Foo f; static void main(String[] args) { println 'Hello from Bar' } }");
    myFixture.addFileToProject("dependent/BarX.java", "class BarX { Foo f; }");
    myFixture.addFileToProject("dependent/BarY.groovy", "class BarY extends FooX { }");

    Runnable checkClassFiles = () -> {
      assert findClassFile("Foo", getModule()) != null;
      assert findClassFile("FooX", getModule()) != null;
      assert findClassFile("Bar", dep) != null;
      assert findClassFile("BarX", dep) != null;

      assert findClassFile("Bar", getModule()) == null;
      assert findClassFile("BarX", getModule()) == null;
      assert findClassFile("Foo", dep) == null;
      assert findClassFile("FooX", dep) == null;
    };

    assertEmpty(make());
    checkClassFiles.run();

    assertEmpty(make());
    checkClassFiles.run();

    assertOutput("Foo", "Hello from Foo", getModule());
    assertOutput("Bar", "Hello from Bar", dep);

    checkClassFiles.run();
  }

public void testCompileTimeConstants() throws ExecutionException {
    myFixture.addFileToProject("Gr.groovy", """
      class Gr {
        public static final String HELLO = "Hello"
        public static final int MAGIC = 239
        public static final Boolean BOOL = true
        public static final boolean bool = true
      }
    """);
    myFixture.addFileToProject("Main.java", """
      public class Main {
        public static void main(String[] args) {
          System.out.println(Gr.HELLO + ", " + Gr.BOOL + Gr.bool + Gr.MAGIC);
        }
      }
    """);
    make();
    assertOutput("Main", "Hello, truetrue239");
  }

  public void test_reporting_rebuild_errors_caused_by_missing_files_excluded_from_compilation() {
    PsiFile foo = myFixture.addFileToProject("Foo.groovy", "class Foo {}");
    myFixture.addFileToProject("Bar.groovy", "class Bar extends Foo {}");

    make();

    excludeFromCompilation(foo);

    GroovyCompilerTestCase.shouldFail(rebuild());
  }

  public void test_compile_groovy_excluded_from_stub_generation() {
    PsiFile foo = myFixture.addFileToProject("Foo.groovy", "class Foo {}");
    myFixture.addFileToProject("Bar.groovy", "class Bar extends Foo {}");

    excludeFromCompilation(GroovyCompilerConfiguration.getInstance(getProject()).getExcludeFromStubGeneration(), foo);

    assert make().isEmpty();
  }

  private void excludeFromCompilation(PsiFile foo) {
    excludeFromCompilation(CompilerConfiguration.getInstance(getProject()).getExcludedEntriesConfiguration(), foo);
  }

  private void excludeFromCompilation(ExcludesConfiguration configuration, PsiFile foo) {
    configuration.addExcludeEntryDescription(new ExcludeEntryDescription(foo.getVirtualFile(), false, true, myFixture.getTestRootDisposable()));
  }

  public void test_make_stub_level_error_and_correct_it() throws IOException {
    PsiFile foo = myFixture.addFileToProject("Foo.groovy", "class Foo { }");
    myFixture.addFileToProject("Bar.java", "class Bar extends Foo {}");

    assert make().isEmpty();

    setFileText(foo, "class Foo implements Runnabl {}");

    String compilerTempRoot = BuildManager.getInstance().getProjectSystemDirectory(getProject()).getAbsolutePath();
    VfsRootAccess.allowRootAccess(getTestRootDisposable(),
                                  compilerTempRoot);//because compilation error points to file under 'groovyStubs' directory
    GroovyCompilerTestCase.shouldFail(make());

    setFileText(foo, "class Foo {}");

    assert make().isEmpty();
  }

  public void test_reporting_module_compile_errors_caused_by_missing_files_excluded_from_compilation() {
    PsiFile foo = myFixture.addFileToProject("Foo.groovy", "class Foo {}");
    myFixture.addFileToProject("Bar.groovy", "class Bar extends Foo {}");

    make();

    excludeFromCompilation(foo);

    GroovyCompilerTestCase.shouldFail(compileModule(getModule()));
  }

  public void test_stubs_generated_while_processing_groovy_class_file_dependencies() throws IOException {
    PsiFile foo = myFixture.addFileToProject("Foo.groovy", "class Foo { }");
    PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar extends Foo { }");
    PsiFile client = myFixture.addFileToProject("Client.groovy", "class Client { Bar bar = new Bar() }");
    PsiFile java =
      myFixture.addFileToProject("Java.java", "class Java extends Client { String getName(Bar bar) { return bar.toString();  } }");

    assert make().isEmpty();

    setFileText(bar, "class Bar { }");

    assert make().isEmpty();
    assert findClassFile("Client") != null;
  }

  public void test_ignore_groovy_internal_non_existent_interface_helper_inner_class() {
    myFixture.addFileToProject("Foo.groovy", """
      interface Foo {}
    
      class Zoo {
        Foo foo() {}
        static class Inner implements Foo {}
      }
    """);
    PsiFile bar = myFixture.addFileToProject("Bar.groovy", "class Bar { def foo = new Zoo.Inner() {}  }");

    assert make().isEmpty();
    assert compileFiles(bar.getVirtualFile()).isEmpty();
  }

public void test_multiline_strings() {
    myFixture.addFileToProject("Foo.groovy", """
      class Foo {
        public static final String s = '''
          multi
          line
          string
        '''
      }
    """);
    myFixture.addFileToProject("Bar.java", "class Bar extends Foo {} ");

    assert make().isEmpty();
  }

  protected boolean isRebuildExpectedAfterChangesInGroovyWhichUseJava() {
    return true;
  }

  public void test_inner_java_class_references_with_incremental_recompilation() throws IOException {
    PsiFile bar1 = myFixture.addFileToProject("bar/Bar1.groovy", "package bar; class Bar1 extends Bar2 { } ");
    myFixture.addFileToProject("bar/Bar2.java", "package bar; class Bar2 extends Bar3 { } ");
    PsiFile bar3 = myFixture.addFileToProject("bar/Bar3.groovy", "package bar; class Bar3 { Bar1 property } ");

    myFixture.addClass("package foo; public class Outer { public static class Inner extends bar.Bar1 { } }");
    PsiFile using = myFixture.addFileToProject("UsingInner.groovy", "import foo.Outer; class UsingInner extends bar.Bar1 { Outer.Inner property } ");

    assert make().isEmpty();

    if (AdvancedSettings.getBoolean("compiler.unified.ic.implementation")) {
      long oldBar1Stamp = findClassFile("bar/Bar1").lastModified();
      long oldBar2Stamp = findClassFile("bar/Bar2").lastModified();
      long oldBar3Stamp = findClassFile("bar/Bar3").lastModified();

      touch(bar1.getVirtualFile());
      touch(bar3.getVirtualFile());
      GroovyCompilerTestCase.shouldSucceed(make());

      assert oldBar1Stamp != findClassFile("bar/Bar1").lastModified();
      assert oldBar2Stamp != findClassFile("bar/Bar2").lastModified();
      assert oldBar3Stamp != findClassFile("bar/Bar3").lastModified();
    } else {
      touch(bar1.getVirtualFile());
      touch(bar3.getVirtualFile());
      touch(using.getVirtualFile());

      if (isRebuildExpectedAfterChangesInGroovyWhichUseJava()){
        assert ContainerUtil.map(make(), it -> it.getMessage()).equals(chunkRebuildMessage("Groovy compiler"));
      } else {
        assert make().isEmpty();
      }
    }
  }

  public void test_rename_class_to_java_and_touch_its_usage() throws IOException {
    PsiFile usage = myFixture.addFileToProject("Usage.groovy", "class Usage { Renamed r } ");
    PsiFile renamed = myFixture.addFileToProject("Renamed.groovy", "public class Renamed { } ");
    assert make().isEmpty();

    touch(usage.getVirtualFile());
    setFileName(renamed, "Renamed.java");
    assert make().isEmpty();
  }

  public void test_compiling_static_extension() throws ExecutionException {
    setupTestSources();
    myFixture.addFileToProject("src/extension/Extension.groovy", """
      package extension
      import groovy.transform.CompileStatic
      
      @CompileStatic class Extension {
          static <T> T test2(List<T> self) {
              self.first()
          }
      }
    """);
    myFixture.addFileToProject("src/META-INF/services/org.codehaus.groovy.runtime.ExtensionModule", """
      moduleName=extension-verify
      moduleVersion=1.0-test
      extensionClasses=extension.Extension
      staticExtensionClasses=
    """);
    myFixture.addFileToProject("tests/AppTest.groovy", """
      class AppTest {
          @groovy.transform.CompileStatic
          static main(args) {
              List<String> list = new ArrayList<>()
              list.add("b")
              list.add("c")
              println list.test2()
          }
      }
""");
    assert make().isEmpty();
    assertOutput("AppTest", "b");
  }

  public void test_no_groovy_library() {
    myFixture.addFileToProject("dependent/a.groovy", "");
    addModule("dependent", true);

    List<CompilerMessage> messages = make();
    assert ContainerUtil.exists(messages, it -> it.getMessage().contains("Cannot compile Groovy files: no Groovy library is defined for module 'dependent'"));
  }

  public void testGroovyOutputIsInstrumented() throws ExecutionException {
    myFixture.addFileToProject("Bar.groovy", """
      import org.jetbrains.annotations.NotNull;
      public class Bar {
        void xxx(@NotNull String param) { println param }
        static void main(String[] args) { new Bar().xxx(null) }
      }
    """);
    File annotations = new File(PathManager.getJarPathForClass(NotNull.class));
    PsiTestUtil.addLibrary(getModule(), "annotations", annotations.getParent(), annotations.getName());

    assert make().isEmpty();

    final Ref<Boolean> exceptionFound = Ref.create(Boolean.FALSE);
    ProcessHandler process = runProcess("Bar", getModule(), DefaultRunExecutor.class, new ProcessAdapter() {
                                          @Override
                                          public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                                            DefaultGroovyMethods.println(this, "stdout: " + event.getText());
                                            if (!ProcessOutputTypes.SYSTEM.equals(outputType)) {
                                              if (!exceptionFound.get()) {
                                                exceptionFound.set(event.getText().contains(
                                                  "java.lang.IllegalArgumentException: Argument for @NotNull parameter 'param' of Bar.xxx must not be null"));
                                              }
                                            }
                                          }
                                        }, ProgramRunner.PROGRAM_RUNNER_EP.findExtension(DefaultJavaProgramRunner.class));
    process.waitFor();

    assert exceptionFound.get();
  }

public void test_extend_groovy_classes_with_additional_dependencies() {
    PsiTestUtil.addProjectLibrary(getModule(), "junit", IntelliJProjectConfiguration.getProjectLibraryClassesRootPaths("JUnit3"));
    TestLibrary library = getGroovyLibrary();
    String coordinate = DefaultGroovyMethods.first((DefaultGroovyMethods.asType(library, RepositoryTestLibrary.class)).getCoordinates());
    if (!coordinate.contains(":groovy-all:")){
      RepositoryTestLibrary testLibrary = new RepositoryTestLibrary(coordinate.replace(":groovy:", ":groovy-test:"));
      testLibrary.addTo(getModule());
    }
    if (coordinate.contains(":2.")) {
      myFixture.addFileToProject("a.groovy", "class Foo extends groovy.util.GroovyTestCase {}");
    }
    else {
      myFixture.addFileToProject("a.groovy", "class Foo extends groovy.test.GroovyTestCase {}");
    }

    assert make().isEmpty();
  }

public void test_java_depends_on_stub_whose_generation_failed() {
    CompilerConfiguration.getInstance(getProject()).setParallelCompilationOption(ParallelCompilationOption.DISABLED);
    Function<String, Runnable> createFiles = (prefix) -> {
      final PsiFile genParam = myFixture.addFileToProject(prefix + "GenParam.java", "class GenParam {}");
      myFixture.addFileToProject(prefix + "Intf.java", "class Intf<T extends GenParam> {}");
      myFixture.addFileToProject(prefix + "SuperFoo.java", "class SuperFoo extends Intf<GenParam> {}");
      final PsiFile fooGroovy = myFixture.addFileToProject(prefix + "Foo.groovy", "class Foo extends SuperFoo {}");
      return () -> {
        try {
          touch(genParam.getVirtualFile());
          touch(fooGroovy.getVirtualFile());
        }
        catch (IOException e) { throw new RuntimeException(e); }
        myFixture.addFileToProject(prefix + "Bar.java", "class Bar extends Foo { }");
      };
    };

    addGroovyLibrary(addModule("mod2", true));

    Runnable touch1 = createFiles.apply("");
    Runnable touch2 = createFiles.apply("mod2/");

    assert make().isEmpty();

    touch1.run();
    touch2.run();

    assert make().stream().noneMatch(it -> it.getCategory() == CompilerMessageCategory.ERROR);
  }

  public void test_recompile_one_file_that_triggers_chunk_rebuild_inside() throws IOException {
    do_test_recompile_one_file_that_triggers_chunk_rebuild_inside(this instanceof GroovycTestBase);
  }

  protected final void do_test_recompile_one_file_that_triggers_chunk_rebuild_inside(boolean expectRebuild) throws IOException {
    myFixture.addFileToProject("BuildContext.groovy", """
      @groovy.transform.CompileStatic 
      class BuildContext {
        static BuildContext createContext(PropTools tools) { return BuildContextImpl.create(tools) } 
      }
    """);
    myFixture.addFileToProject("PropTools.groovy", "class PropTools { SomeTool someTool }");
    myFixture.addFileToProject("SomeTool.groovy", "interface SomeTool { void call(BuildContext ctx) }");
    String subText = """
      @groovy.transform.CompileStatic 
      class BuildContextImpl extends BuildContext {
        static BuildContextImpl create(PropTools tools) { return new BuildContextImpl() }
        void foo(SomeTool tool) { tool.call(this) } 
      }
    """;
    PsiFile sub = myFixture.addFileToProject("BuildContextImpl.groovy", subText);
    assert make().isEmpty();

    setFileText(sub, subText + " ");

    List<CompilerMessage> makeMessages = make();
    List<CompilerMessage> fileMessages = compileFiles(sub.getVirtualFile());
    if (expectRebuild) {
      assert ContainerUtil.map(makeMessages, it -> it.getMessage()).equals(chunkRebuildMessage("Groovy compiler"));
      assert ContainerUtil.exists(fileMessages, it -> it.getMessage().contains("Consider building whole project or rebuilding the module"));
    }
    else {
      assert makeMessages.isEmpty();
      assert fileMessages.isEmpty();
    }
  }

  public void test_report_real_compilation_errors() {
    addModule("another", true);

    myFixture.addClass("class Foo {}");
    myFixture.addFileToProject("a.groovy", "import goo.Goo; class Bar { }");
    GroovyCompilerTestCase.shouldFail(compileModule(getModule()));
  }

  record JDKItem(LanguageLevel level, String name, Integer code) {}

  public void test_honor_bytecode_version() throws IOException {
    JDKItem base;
    JDKItem old;
    if (getGroovyLibrary().equals(GroovyProjectDescriptors.LIB_GROOVY_2_4)) {
      base = new JDKItem(LanguageLevel.JDK_1_8, "1.8", Opcodes.V1_8);
      old = new JDKItem(LanguageLevel.JDK_1_6, "1.6", Opcodes.V1_6);
    }
    else {
      base = new JDKItem(LanguageLevel.JDK_11, "11", Opcodes.V11);
      old = new JDKItem(LanguageLevel.JDK_1_8, "1.8", Opcodes.V1_8);
    }

    IdeaTestUtil.setModuleLanguageLevel(getModule(), base.level);
    CompilerConfiguration.getInstance(getProject()).setBytecodeTargetLevel(getModule(), base.name);

    myFixture.addFileToProject("a.groovy", "class Foo { }");
    assert make().isEmpty();
    assert getClassFileVersion("Foo") == base.code;

    IdeaTestUtil.setModuleLanguageLevel(getModule(), old.level);
    CompilerConfiguration.getInstance(getProject()).setBytecodeTargetLevel(getModule(), old.name);
    assert rebuild().isEmpty();
    assert getClassFileVersion("Foo") == old.code;
  }

  private int getClassFileVersion(String className) throws IOException {
    File classFile = findClassFile(className);
    final int[] version = {-1};
    new ClassReader(FileUtil.loadFileBytes(classFile)).accept(new ClassVisitor(Opcodes.ASM9) {
      @Override
      public void visit(int v, int access, String name, String signature, String superName, String[] interfaces) {
        version[0] = v;
      }
    }, 0);
    return version[0];
  }

  public void test_using_trait_from_java() {
    myFixture.addFileToProject("a.groovy", "trait Foo { }");
    myFixture.addFileToProject("b.java", "class Bar implements Foo { Foo f; }");
    assert make().isEmpty();

    final var config = CompilerConfiguration.getInstance(getProject());
    config.setBuildProcessVMOptions(config.getBuildProcessVMOptions() + " -D" + JpsGroovycRunner.GROOVYC_IN_PROCESS + "=false");
    assert rebuild().isEmpty();
  }
}
