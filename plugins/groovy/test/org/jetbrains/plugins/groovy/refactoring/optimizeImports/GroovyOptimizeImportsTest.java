// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.refactoring.optimizeImports;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.CodeInsightWorkspaceSettings;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.psi.impl.source.PostprocessReformattingAspect;
import com.intellij.testFramework.fixtures.impl.CodeInsightTestFixtureImpl;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.editor.GroovyImportOptimizer;
import org.jetbrains.plugins.groovy.util.TestUtils;

public class GroovyOptimizeImportsTest extends LightGroovyTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    CodeInsightWorkspaceSettings.getInstance(getProject()).setOptimizeImportsOnTheFly(true, getTestRootDisposable());
    ((CodeInsightTestFixtureImpl)myFixture).canChangeDocumentDuringHighlighting(true);
  }

  public void testNewline() {
    doTest();
  }

  public void testAliased() {
    doTest();
  }

  public void testSimpleOptimize() {
    doTest();
  }

  public void testCommented() {
    doTest();
  }

  public void testOptimizeExists() {
    doTest();
  }

  public void testOptimizeAlias() {
    doTest();
  }

  public void testFoldImports() {
    doTest();
  }

  public void testFoldImports2() {
    doTest();
  }

  public void testUntypedCall() {
    doTest();
  }

  public void testFoldImports3() {
    doTest();
  }

  public void testFoldImports4() {
    doTest();
  }

  public void testFoldImports5() {
    doTest();
  }

  public void testFixPoint() {
    doTest();
  }

  public void testPreserveImportAnnotations() { doTest(); }

  public void testUtilListMasked() {
    myFixture.addClass("package java.awt; public class List {}");
    doTest();
  }

  public void testExtraLineFeed() {
    myFixture.addClass("package groovy.io; public class EncodingAwareBufferedWriter {}");
    myFixture.addClass("package groovy.io; public class PlatformLineWriter {}");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".groovy", "foo/bar.groovy"));
    doOptimizeImports();
    doOptimizeImports();
    myFixture.checkResultByFile(getTestName(false) + ".groovy");
  }

  public void testSemicolons() {
    doTest();
  }

  public void testSameFile() {
    doTest();
  }

  public void testSamePackage() {
    myFixture.addClass("package foo; public class Bar {}");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".groovy", "foo/Foo.groovy"));
    doOptimizeImports();
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testQualifiedUsage() {
    myFixture.addClass("package foo; public class Bar {}");
    doTest();
  }

  public void testFileHeader() {
    doTest();
  }

  public void testRemoveImplicitlyImported() { doTest(); }

  public void testRemoveImplicitlyDemandImported() { doTest(); }

  public void testDontRemoveRedImports() { doTest(); }

  public void testDontRemoveRedImports2() { doTest(); }

  public void testDontRemoveRedImports3() { doTest(); }

  public void testDontRemoveRedImports4() { doTest(); }

  public void testDontRemoveRedImports5() { doTest(); }

  public void testDontRemoveRedImports6() { doTest(); }

  public void testDontRemoveRedImports7() { doTest(); }

  public void testDontRemoveRedImports8() { doTest(); }

  public void testDontRemoveRedImports9() { doTest(); }

  public void testRemoveSamePackaged() {
    myFixture.addClass("package foo.bar; public class Aaaa {}");
    myFixture.addClass("package foo.bar; public class Bbbb {}");
    myFixture.addClass("package foo.bar; public class Zzzz {}");
    myFixture.configureFromExistingVirtualFile(myFixture.copyFileToProject(getTestName(false) + ".groovy", "foo/Foo.groovy"));
    doOptimizeImports();
    myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
  }

  public void testJustWrongImport() {
    myFixture.configureByText("a.groovy", "import a.b.c.d; final d c;");
    doOptimizeImports();
    myFixture.checkResult("import a.b.c.d; final d c;");
  }

  public void testCleanBeforeJavadoc() {
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

  public void testInnerInnerClass() { doTest(); }

  private void doTest() {
    CodeStyle.doWithTemporarySettings(getProject(), CodeStyle.getSettings(getProject()), settings -> {
      myFixture.configureByFile(getTestName(false) + ".groovy");

      doOptimizeImports();
      PostprocessReformattingAspect.getInstance(getProject()).doPostponedFormatting();
      myFixture.checkResultByFile(getTestName(false) + "_after.groovy");
    });
  }

  private void doOptimizeImports() {
    GroovyImportOptimizer optimizer = new GroovyImportOptimizer();
    final Runnable runnable = optimizer.processFile(myFixture.getFile());

    CommandProcessor.getInstance().executeCommand(
      getProject(), () -> ApplicationManager.getApplication().runWriteAction(runnable), "Optimize imports", null);
  }

  public void testOptimizeOnTheFly() {
    myFixture.configureFromExistingVirtualFile(myFixture.addFileToProject("a.groovy", "import java.lang.String<caret>").getVirtualFile());
    //myFixture.configureByText "a.groovy", "import java.lang.String<caret>"
    myFixture.type("""
                     
                     
                     foo""");
    myFixture.doHighlighting();
    myFixture.checkResult("foo<caret>");
  }

  public void testNoOptimizeOnTheFlyDuringEditing() {
    myFixture.configureByText("a.groovy", "import java.util.String<caret>");
    myFixture.type("\nimport java.lang.String");
    myFixture.doHighlighting();
    myFixture.checkResult("import java.util.String\nimport java.lang.String<caret>");
  }

  public void testNoOptimizeOnDummyChange() {
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

  public void testSorting() {
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
  }

  public void testAnnotationOnUnusedImport1() {
    myFixture.addClass("package groovyx.gpars; public class GParsPool{}");
    myFixture.addClass("package groovyx.gpars; public class GParsExecutorsPool{}");

    myFixture.configureByText("_.groovy",
                              """
                                @Grab(group='org.codehaus.gpars', module='gpars', version='0.12')
                                import groovyx.gpars.GParsPool
                                import groovyx.gpars.GParsExecutorsPool
                                
                                GParsExecutorsPool oi
                                """);
    doOptimizeImports();

    myFixture.checkResult(
      """
        @Grab(group = 'org.codehaus.gpars', module = 'gpars', version = '0.12')
        import groovyx.gpars.GParsExecutorsPool
        
        GParsExecutorsPool oi
        """);
  }

  public void testAnnotationOnUnusedImport2() {
    myFixture.addClass("package groovyx.gpars; public class GParsPool{}");

    myFixture.configureByText("_.groovy",
                              """
                                @Grab(group='org.codehaus.gpars', module='gpars', version='0.12')
                                import groovyx.gpars.GParsPool
                                """);
    doOptimizeImports();

    myFixture.checkResult("""
                            @Grab(group = 'org.codehaus.gpars', module = 'gpars', version = '0.12')
                            import java.lang.Object
                            """);
  }

  public void testAnnotationOnUnusedImport3() {
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
    doOptimizeImports();

    myFixture.checkResult(
      """
        @Grab(group = 'org.codehaus.gpars', module = 'gpars', version = '0.12')
        @Grab(group = 'org.codehaus.gpars', module = 'gpars', version = '0.12')
        import groovyx.gpars.GParsExecutorsPool
        
        GParsExecutorsPool oi
        """);
  }

  public void test_do_not_remove_space_between_package_and_imports() {
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
  }

  public void test__Newify_with_pattern() {
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
  }

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

  public void test_same_package() {
    myFixture.addClass("package foo; public class A {}");
    doTest("package foo; import foo.A; class B { A a; }", """
      package foo
      
      class B { A a; }""");
  }

  private void doTest(String before, String after) {
    myFixture.configureByText("_.groovy", before);
    doOptimizeImports();
    myFixture.checkResult(after);
  }

  @Override
  public final String getBasePath() {
    return TestUtils.getTestDataPath() + "optimizeImports/";
  }
}
