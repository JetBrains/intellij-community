// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.optimizeImports;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;

@TestApplication
public class GroovyOptimizeImportsTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_6_0_REAL_JDK);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "optimizeImports/");
    CodeInsightWorkspaceSettings.getInstance(myFixture.getProject()).setOptimizeImportsOnTheFly(true, myFixture.getTestRootDisposable());
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
  }

  @Test
  public void testNewline() { doTest(); }
  @Test
  public void testAliased() { doTest(); }
  @Test
  public void testSimpleOptimize() { doTest(); }
  @Test
  public void testCommented() { doTest(); }
  @Test
  public void testOptimizeExists() { doTest(); }
  @Test
  public void testOptimizeAlias() { doTest(); }
  @Test
  public void testFoldImports() { doTest(); }
  @Test
  public void testFoldImports2() { doTest(); }
  @Test
  public void testUntypedCall() { doTest(); }
  @Test
  public void testFoldImports3() { doTest(); }
  @Test
  public void testFoldImports4() { doTest(); }
  @Test
  public void testFoldImports5() { doTest(); }
  @Test
  public void testFixPoint() { doTest(); }
  @Test
  public void testPreserveImportAnnotations() { doTest(); }
  @Test
  public void testModuleImports1() { doTest(); }
  @Test
  public void testModuleImports2() { doTest(); }

  @Test
  public void testUtilListMasked() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package java.awt; public class List {}");
    doTest();
  }

  @Test
  public void testExtraLineFeed() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package groovy.io; public class EncodingAwareBufferedWriter {}");
    myFixture.addClass("package groovy.io; public class PlatformLineWriter {}");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(myFixture.getTestName(false) + ".groovy", "foo/bar.groovy"));
    EdtTestUtil.runInEdtAndWait(() -> {
      doOptimizeImports();
      doOptimizeImports();
      myFixture.checkResultByFile(myFixture.getTestName(false) + ".groovy");
    });
  }

  @Test
  public void testSemicolons() { doTest(); }
  @Test
  public void testSameFile() { doTest(); }

  @Test
  public void testSamePackage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public class Bar {}");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(myFixture.getTestName(false) + ".groovy", "foo/Foo.groovy"));
    EdtTestUtil.runInEdtAndWait(() -> {
      doOptimizeImports();
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
    });
  }

  @Test
  public void testQualifiedUsage() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public class Bar {}");
    doTest();
  }

  @Test
  public void testFileHeader() { doTest(); }
  @Test
  public void testRemoveImplicitlyImported() { doTest(); }
  @Test
  public void testRemoveImplicitlyDemandImported() { doTest(); }
  @Test
  public void testDontRemoveRedImports() { doTest(); }
  @Test
  public void testDontRemoveRedImports2() { doTest(); }
  @Test
  public void testDontRemoveRedImports3() { doTest(); }
  @Test
  public void testDontRemoveRedImports4() { doTest(); }
  @Test
  public void testDontRemoveRedImports5() { doTest(); }
  @Test
  public void testDontRemoveRedImports6() { doTest(); }
  @Test
  public void testDontRemoveRedImports7() { doTest(); }
  @Test
  public void testDontRemoveRedImports8() { doTest(); }
  @Test
  public void testDontRemoveRedImports9() { doTest(); }

  @Test
  public void testRemoveSamePackaged() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo.bar; public class Aaaa {}");
    myFixture.addClass("package foo.bar; public class Bbbb {}");
    myFixture.addClass("package foo.bar; public class Zzzz {}");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(myFixture.getTestName(false) + ".groovy", "foo/Foo.groovy"));
    EdtTestUtil.runInEdtAndWait(() -> {
      doOptimizeImports();
      myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
    });
  }

  @Test
  public void testJustWrongImport() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", "import a.b.c.d; final d c;");
    EdtTestUtil.runInEdtAndWait(() -> {
      doOptimizeImports();
      myFixture.checkResult("import a.b.c.d; final d c;");
    });
  }

  @Test
  public void testCleanBeforeJavadoc() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", """
      import javax.swing.JFrame
      
      /**
       * some javadoc
       */
      class Fooxx<caret>{
      }""");
    myFixture.type(" ");
    myFixture.doHighlighting();
    //    doOptimizeImports();
    myFixture.checkResult("""
                            /**
                             * some javadoc
                             */
                            class Fooxx <caret>{
                            }""");
  }

  @Test
  public void testInnerInnerClass() { doTest(); }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      CodeStyle.doWithTemporarySettings(myFixture.getProject(), CodeStyle.getSettings(myFixture.getProject()), settings -> {
        myFixture.configureByFile(myFixture.getTestName(false) + ".groovy");

        doOptimizeImports();
        PostprocessReformattingAspect.getInstance(myFixture.getProject()).doPostponedFormatting();
        myFixture.checkResultByFile(myFixture.getTestName(false) + "_after.groovy");
      });
    });
  }

  private void doOptimizeImports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      GroovyImportOptimizer optimizer = new GroovyImportOptimizer();
      final Runnable runnable = optimizer.processFile(myFixture.getFile());

      CommandProcessor.getInstance().executeCommand(
        myFixture.getProject(), () -> ApplicationManager.getApplication().runWriteAction(runnable), "Optimize imports", null);
    });
  }

  @Test
  public void testOptimizeOnTheFly() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("a.groovy", "import java.lang.String<caret>").getVirtualFile());
    //myFixture.configureByText "a.groovy", "import java.lang.String<caret>"
    myFixture.type("""

                     foo""");
    myFixture.doHighlighting();
    myFixture.checkResult("foo<caret>");
  }

  @Test
  public void testNoOptimizeOnTheFlyDuringEditing() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", "import java.util.String<caret>");
    myFixture.type("\nimport java.lang.String");
    myFixture.doHighlighting();
    myFixture.checkResult("import java.util.String\nimport java.lang.String<caret>");
  }

  @Test
  public void testNoOptimizeOnDummyChange() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public class Foo {}");
    myFixture.configureByText("a.groovy", """
      import foo.Foo
      
      def foo() { <caret> Foo f}""");
    myFixture.doHighlighting();
    myFixture.type("\n");
    myFixture.doHighlighting();
    myFixture.checkResult("""
                            import foo.Foo
                            
                            def foo() {\s
                                <caret>Foo f}""");
  }

  @Test
  public void testSorting() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public class Foo{}");
    myFixture.addClass("package foo; public class Bar{public static void foo0(){}}");
    myFixture.addClass("package java.test; public class Test{public static void foo(){}}");
    myFixture.addClass("package java.test2; public class Test2{public static void foo2(){}}");
    myFixture.addClass("package test; public class Alias{public static void test(){}}");
    myFixture.addClass("package test; public class Alias2{public static void test2(){}}");

    myFixture.configureByText("__a.groovy",
                              """
                                package pack
                                
                                
                                import foo.Foo
                                import foo.Bar
                                import java.test.Test
                                import java.test2.Test2
                                import static foo.Bar.foo0
                                import static java.test.Test.foo
                                import static java.test2.Test2.*
                                import static test.Alias.test as aliased
                                import static test.Alias2.test2 as aliased2
                                
                                new Foo()
                                new Bar()
                                new Test()
                                new Test2()
                                foo()
                                foo0()
                                foo1()
                                aliased()
                                aliased2()
                                """);

    EdtTestUtil.runInEdtAndWait(() -> {
      doOptimizeImports();

      myFixture.checkResult(
        """
          package pack
          
          import foo.Bar
          import foo.Foo
          
          import java.test.Test
          import java.test2.Test2
          
          import static foo.Bar.foo0
          import static java.test.Test.foo
          import static test.Alias.test as aliased
          import static test.Alias2.test2 as aliased2
          
          new Foo()
          new Bar()
          new Test()
          new Test2()
          foo()
          foo0()
          foo1()
          aliased()
          aliased2()
          """);
    });
  }

  @Test
  public void testAnnotationOnUnusedImport1() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package groovyx.gpars; public class GParsPool{}");
    myFixture.addClass("package groovyx.gpars; public class GParsExecutorsPool{}");

    myFixture.configureByText("_.groovy",
                              """
                                @Grab(group='org.codehaus.gpars', module='gpars', version='0.12')
                                import groovyx.gpars.GParsPool
                                import groovyx.gpars.GParsExecutorsPool
                                
                                GParsExecutorsPool oi
                                """);
    EdtTestUtil.runInEdtAndWait(() -> {
      doOptimizeImports();

      myFixture.checkResult(
        """
          @Grab(group = 'org.codehaus.gpars', module = 'gpars', version = '0.12')
          import groovyx.gpars.GParsExecutorsPool
          
          GParsExecutorsPool oi
          """);
    });
  }

  @Test
  public void testAnnotationOnUnusedImport2() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package groovyx.gpars; public class GParsPool{}");

    myFixture.configureByText("_.groovy",
                              """
                                @Grab(group='org.codehaus.gpars', module='gpars', version='0.12')
                                import groovyx.gpars.GParsPool
                                """);
    EdtTestUtil.runInEdtAndWait(() -> {
      doOptimizeImports();

      myFixture.checkResult("""
                              @Grab(group = 'org.codehaus.gpars', module = 'gpars', version = '0.12')
                              import java.lang.Object
                              """);
    });
  }

  @Test
  public void testAnnotationOnUnusedImport3() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package groovyx.gpars; public class GParsPool{}");
    myFixture.addClass("package groovyx.gpars; public class GParsExecutorsPool{}");

    myFixture.configureByText("_.groovy",
                              """
                                @Grab(group='org.codehaus.gpars', module='gpars', version='0.12')
                                @Grab(group='org.codehaus.gpars', module='gpars', version='0.12')
                                import groovyx.gpars.GParsPool
                                import groovyx.gpars.GParsExecutorsPool
                                
                                GParsExecutorsPool oi
                                """);
    EdtTestUtil.runInEdtAndWait(() -> {
      doOptimizeImports();

      myFixture.checkResult(
        """
          @Grab(group = 'org.codehaus.gpars', module = 'gpars', version = '0.12')
          @Grab(group = 'org.codehaus.gpars', module = 'gpars', version = '0.12')
          import groovyx.gpars.GParsExecutorsPool
          
          GParsExecutorsPool oi
          """);
    });
  }

  @Test
  public void test_do_not_remove_space_between_package_and_imports() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package some; class Import {}");
    myFixture.addClass("package some; class ImportToBeDeleted {}");
    myFixture.addClass("package some; class OtherImport {}");
    myFixture.configureByText("_.groovy",
                              """
                                package pkg
                                
                                import some.OtherImport
                                import some.Import
                                import some.ImportToBeDeleted
                                
                                class MyClass {
                                  def a = new Import()
                                  def b = new OtherImport()
                                }
                                """);
    EdtTestUtil.runInEdtAndWait(() -> {
      doOptimizeImports();
      myFixture.checkResult(
        """
          package pkg
          
          import some.Import
          import some.OtherImport
          
          class MyClass {
            def a = new Import()
            def b = new OtherImport()
          }
          """);
    });
  }

  @Test
  public void test__Newify_with_pattern() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package hello; public class Abc {}");
    myFixture.configureByText("_.groovy",
                              """
                                import groovy.transform.CompileStatic
                                import hello.Abc
                                
                                import java.lang.Integer
                                
                                @Newify(pattern = /[A-Z][A-Za-z0-9_]+/)
                                @CompileStatic
                                void newifyImportsIncorrectlyMarkedAsUnused() {
                                    final a = Integer(1)
                                    def b = Abc()
                                }
                                """);
    EdtTestUtil.runInEdtAndWait(() -> {
      doOptimizeImports();
      myFixture.checkResult(
        """
          import groovy.transform.CompileStatic
          import hello.Abc
          
          @Newify(pattern = /[A-Z][A-Za-z0-9_]+/)
          @CompileStatic
          void newifyImportsIncorrectlyMarkedAsUnused() {
              final a = Integer(1)
              def b = Abc()
          }
          """);
    });
  }

  @Test
  public void test_annotated_unresolved_import() {
    doTest(
      """
        import groovy.xml.XmlUtil
        import javax.security.auth.AuthPermission
        @Grab("coordinates")
        import org.foo.Bar
        
        XmlUtil xx
        Bar bar
        """,
      """
        import groovy.xml.XmlUtil
        @Grab("coordinates")
        import org.foo.Bar
        
        XmlUtil xx
        Bar bar
        """);
  }

  @Test
  public void test_same_package() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package foo; public class A {}");
    doTest("package foo; import foo.A; class B { A a; }", """
      package foo
      
      class B { A a; }""");
  }

  private void doTest(String before, String after) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("_.groovy", before);
      doOptimizeImports();
      myFixture.checkResult(after);
    });
  }
}
