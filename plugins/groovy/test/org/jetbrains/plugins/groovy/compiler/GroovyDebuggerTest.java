// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.compiler;

import com.intellij.debugger.engine.DebugProcessEvents;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.openapi.application.ActionsKt;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestLoggerFactory;
import com.intellij.testFramework.ThreadTracker;
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.runner.GroovyScriptRunConfiguration;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class GroovyDebuggerTest extends GroovyCompilerTestCase implements DebuggerMethods {
  @Override
  public Logger getLogger() { return LOG; }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    addGroovyLibrary(getModule());
    enableDebugLogging();
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      ThreadTracker.awaitJDIThreadsTermination(100, TimeUnit.SECONDS);
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  private void enableDebugLogging() {
    TestLoggerFactory.enableDebugLogging(myFixture.getTestRootDisposable(), DebugProcessImpl.class, DebugProcessEvents.class,
                                         GroovyDebuggerTest.class);
    LOG.info(getTestStartedLogMessage());
  }

  private String getTestStartedLogMessage() {
    return "Starting " + getClass().getName() + "." + getTestName(false);
  }

  @Override
  protected void runTestRunnable(@NotNull ThrowableRunnable<Throwable> testRunnable) throws Throwable {
    try {
      super.runTestRunnable(testRunnable);
    }
    catch (Throwable e) {
      TestLoggerFactory.dumpLogToStdout(getTestStartedLogMessage());
      throw e;
    }
  }

  public void runDebugger(PsiFile script, Runnable cl) throws RuntimeException {
    GroovyScriptRunConfiguration configuration = createScriptConfiguration(script.getVirtualFile().getPath(), getModule());
    runDebugger(configuration, cl);
  }

  public void testVariableInScript() {
    PsiFile file = myFixture.addFileToProject("Foo.groovy",
                                              "def a = 2\n" +
                                              "a");
    addBreakpoint("Foo.groovy", 1);
    runDebugger(file, () -> {
      waitForBreakpoint();
      eval("a", "2");
      eval("2?:3", "2");
      eval("null?:3", "3");
    });
  }

  public void testVariableInsideClosure() {
    PsiFile file = myFixture.addFileToProject("Foo.groovy", """
      def a = 2
      Closure c = {
        a++;
        a    //3
      }
      c()
      a++""");
    addBreakpoint("Foo.groovy", 3);
    runDebugger(file, () -> {
      waitForBreakpoint();
      eval("a", "3");
    });
  }

  public void testQualifyNames() {
    myFixture.addFileToProject("com/Goo.groovy", """
      
      package com
      interface Goo {
        int mainConstant = 42
        int secondConstant = 1
      }
      """);
    myFixture.addFileToProject("com/Foo.groovy", """
      
      package com
      class Foo {
        static bar = 2
        int field = 3
      
        String toString() { field as String }
      }""");


    PsiFile file = myFixture.addFileToProject("com/Bar.groovy", """
      package com
      import static com.Goo.*
      
      def lst = [new Foo()] as Set
      println 2 //4
      """);

    addBreakpoint("com/Bar.groovy", 4);
    make();
    runDebugger(file, () -> {
      waitForBreakpoint();
      eval("Foo.bar", "2");
      eval("mainConstant", "42");
      eval("secondConstant", "1");
      eval("mainConstant - secondConstant", "41");
      eval("(lst as List<Foo>)[0].field", "3");
      eval("lst", "[3]");
      eval("lst.size()", "1");
    });
  }

  public void testCall() {
    PsiFile file = myFixture.addFileToProject("B.groovy", """
      class B {
          def getFoo() {2}
      
          def call(Object... args){
              -1  // 4
          }
      
          public static void main(String[] args) {
              new B().call()
          }
      }""");
    addBreakpoint("B.groovy", 4);
    runDebugger(file, () -> {
      waitForBreakpoint();
      eval("foo", "2");
      eval("getFoo()", "2");
      eval("this.getFoo()", "2");
      eval("this.foo", "2");
      eval("this.call(2)", "-1");
      eval("call(2)", "-1");
      eval("call(foo)", "-1");
    });
  }

  public void testStaticContext() {
    PsiFile file = myFixture.addFileToProject("B.groovy", """
      
      class B {
          public static void main(String[] args) {
              def cl = { a ->
                hashCode() //4
              }
              cl.delegate = "string"
              cl(42) //7
          }
      }""");
    addBreakpoint("B.groovy", 4);
    addBreakpoint("B.groovy", 7);
    runDebugger(file, () -> {
      waitForBreakpoint();
      eval("args.size()", "0");
      eval("cl.delegate.size()", "6");
      resume();
      waitForBreakpoint();
      eval("a", "42");
      eval("size()", "6");
      eval("delegate.size()", "6");
      eval("owner.name", "B");
      eval("this.name", "B");
      eval("[0, 1, 2, 3].collect { int numero -> numero.toString() }", "[0, 1, 2, 3]");
    });
  }

  public void test_closures_in_instance_context_with_delegation() {
    PsiFile file = myFixture.addFileToProject("B.groovy", """
      
      def cl = { a ->
        hashCode() //2
      }
      cl.delegate = "string"
      cl(42) // 5
      
      def getFoo() { 13 }
      """);
    addBreakpoint("B.groovy", 2);
    runDebugger(file, () -> {
      waitForBreakpoint();
      eval("a", "42");
      eval("size()", "6");
      eval("delegate.size()", "6");
      eval("owner.foo", "13");
      eval("this.foo", "13");
      eval("foo", "13");
    });
  }

  public void testClassOutOfSourceRoots() throws IOException {
    final TempDirTestFixtureImpl tempDir = new TempDirTestFixtureImpl();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        tempDir.setUp();
        disposeOnTearDown(() -> {
          try {
            tempDir.tearDown();
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
        PsiTestUtil.addContentRoot(getModule(), tempDir.getFile(""));
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    final AtomicReference<VirtualFile> myClass = new AtomicReference<>();

    final String mcText = """
      
      package foo //1
      
      class MyClass { //3
      static def foo(def a) {
        println a //5
      }
      }
      """;


    EdtTestUtil.runInEdtAndWait(() -> myClass.set(tempDir.createFile("MyClass.groovy", mcText)));

    addBreakpoint(myClass.get(), 5);

    PsiFile file = myFixture.addFileToProject("Foo.groovy", "def cl = new GroovyClassLoader()\n" +
                                                            "cl.parseClass('''" + mcText + "''', 'MyClass.groovy').foo(2)");

    runDebugger(file, () -> {
      waitForBreakpoint();
      assertEquals(myClass.get(), getSourcePosition().getFile().getVirtualFile());
      eval("a", "2");
    });
  }

  public void test_groovy_source_named_java_in_lib_source() {
    final TempDirTestFixtureImpl tempDir = new TempDirTestFixtureImpl();
    EdtTestUtil.runInEdtAndWait(() -> {
      try {
        tempDir.setUp();
        disposeOnTearDown(() -> {
          try {
            tempDir.tearDown();
          }
          catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
        tempDir.createFile("pkg/java.groovy", "class java {}");
        PsiTestUtil.addLibrary(getModule(), "lib", tempDir.getFile("").getPath(), ArrayUtil.EMPTY_STRING_ARRAY, new String[]{""});
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });

    ActionsKt.runReadAction(() -> {
      JavaPsiFacade facade = JavaPsiFacade.getInstance(getProject());
      assertNull(facade.findClass("java", GlobalSearchScope.allScope(getProject())));
      assertNotNull(facade.findPackage("").findClassByShortName("java", GlobalSearchScope.allScope(getProject())));
      return null;
    });

    PsiFile file = myFixture.addFileToProject("Foo.groovy", """
      int a = 42
      int b = 3 //1
      """);

    addBreakpoint(file.getVirtualFile(), 1);

    runDebugger(file, () -> {
      waitForBreakpoint();
      eval("a", "42");
    });
  }

  public void testAnonymousClassInScript() {
    PsiFile file = myFixture.addFileToProject("Foo.groovy", """
      new Runnable() {
        void run() {
          print 'foo'
        }
      }.run()
      
      """);
    addBreakpoint("Foo.groovy", 2);
    runDebugger(file, () -> {
      waitForBreakpoint();
      eval("1+1", "2");
    });
  }

  public void testEvalInStaticMethod() {
    PsiFile file = myFixture.addFileToProject("Foo.groovy", """
      static def foo() {
        int x = 5
        print x
      }
      
      foo()
      
      """);
    addBreakpoint("Foo.groovy", 2);
    runDebugger(file, () -> {
      waitForBreakpoint();
      eval("x", "5");
    });
  }

  public void test_non_identifier_script_name() {
    PsiFile file = myFixture.addFileToProject("foo-bar.groovy", """
      int x = 1
      println "hello"
      """);
    addBreakpoint(file.getName(), 1);
    runDebugger(file, () -> {
      waitForBreakpoint();
      eval("x", "1");
    });
  }

  public void test_navigation_outside_source() {
    final Module module1 = addModule("module1", false);
    Module module2 = addModule("module2", true);
    addGroovyLibrary(module1);
    addGroovyLibrary(module2);
    EdtTestUtil.runInEdtAndWait(() -> ModuleRootModificationUtil.addDependency(getModule(), module1));

    final PsiFile scr = myFixture.addFileToProject("module1/Scr.groovy", "println \"hello\"");
    myFixture.addFileToProject("module2/Scr.groovy", "println \"hello\"");

    addBreakpoint("module1/Scr.groovy", 0);
    runDebugger(scr, () -> {
      waitForBreakpoint();
      assertEquals(scr, getSourcePosition().getFile());
    });
  }

  public void test_in_static_inner_class() {
    PsiFile file = myFixture.addFileToProject("Foo.groovy", """
      
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
      """);
    addBreakpoint("Foo.groovy", 6);
    runDebugger(file, () -> {
      waitForBreakpoint();
      eval("x", "1");
      eval("this", "str");
    });
  }

  public void test_evaluation_within_trait_method() {
    PsiFile file = myFixture.addFileToProject("Foo.groovy", """
      
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
      """);
    addBreakpoint("Foo.groovy", 3);
    runDebugger(file, () -> {
      waitForBreakpoint();
      eval("a", "1");
      eval("b", "3");
      eval("this", "fooInstance");
    });
  }

  public void test_evaluation_in_java_context() {
    PsiFile starterFile = myFixture.addFileToProject("Gr.groovy", """
      new Main().foo()
      """);
    PsiFile file = myFixture.addFileToProject("Main.java", """
      import java.util.Arrays;
      import java.util.List;
      
      public class Main {
        void foo() {
           List<Integer> a = Arrays.asList(1,2,3,4,5,6,7,8,9,10);
           int x = 5; // 7
        }
      }
      """);
    make();

    addBreakpoint(file.getVirtualFile(), 7);
    runDebugger(starterFile, () -> {
      waitForBreakpoint();
      eval("a.find {it == 4}", "4", GroovyFileType.GROOVY_FILE_TYPE);
    });
  }

  public void test_evaluation_in_static_java_context() {
    PsiFile starterFile = myFixture.addFileToProject("Gr.groovy", """
      Main.test()
      """);
    PsiFile file = myFixture.addFileToProject("Main.java", """
      import java.util.Arrays;
      import java.util.List;
      
      public class Main {
        public static void test() {
           List<Integer> a = Arrays.asList(1,2,3,4,5,6,7,8,9,10);
           int x = 5; // 7
        }
      }
      """);
    make();

    addBreakpoint(file.getVirtualFile(), 7);
    runDebugger(starterFile, () -> {
      waitForBreakpoint();
      eval("a.find {it == 6}", "6", GroovyFileType.GROOVY_FILE_TYPE);
    });
  }

  public void test_evaluation_with_java_references_in_java_context() {
    PsiFile starterFile = myFixture.addFileToProject("Gr.groovy", """
      new Main().foo()
      """);
    PsiFile file = myFixture.addFileToProject("Main.java", """
      import java.util.Arrays;
      import java.util.List;
      
      public class Main {
        void foo() {
           List<String> a = Arrays.asList("1","22","333");
           int x = 5; // 7
        }
      }
      """);
    make();

    addBreakpoint(file.getVirtualFile(), 7);
    runDebugger(starterFile, () -> {
      waitForBreakpoint();
      eval("a.findAll {it.length() > 2}.size()", "1", GroovyFileType.GROOVY_FILE_TYPE);
    });
  }

  public void test_evaluation_of_params_in_java_context() {
    PsiFile starterFile = myFixture.addFileToProject("Gr.groovy", """
      new Main().foo((String[])["a", "b", "c"])
      """);
    PsiFile file = myFixture.addFileToProject("Main.java", """
      import java.util.Arrays;
      import java.util.List;
      
      public class Main {
        void foo(String[] a) {
           int x = 5; // 6
        }
      }
      """);
    make();

    addBreakpoint(file.getVirtualFile(), 6);
    runDebugger(starterFile, () -> {
      waitForBreakpoint();
      eval("a[1]", "b", GroovyFileType.GROOVY_FILE_TYPE);
    });
  }

  public void addBreakpoint(final String fileName, int line) {
    final AtomicReference<VirtualFile> file = new AtomicReference<>();
    EdtTestUtil.runInEdtAndWait(() -> file.set(myFixture.getTempDirFixture().getFile(fileName)));
    addBreakpoint(file.get(), line);
  }

  private static final Logger LOG = Logger.getInstance(GroovyDebuggerTest.class);
}
