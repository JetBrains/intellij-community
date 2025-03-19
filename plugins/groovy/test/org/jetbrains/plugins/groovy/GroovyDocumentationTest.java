// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.codeInsight.navigation.CtrlMouseHandler;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import junit.framework.TestCase;
import org.intellij.lang.annotations.Language;
import org.jetbrains.plugins.groovy.lang.documentation.GroovyDocumentationProvider;

public class GroovyDocumentationTest extends LightJavaCodeInsightFixtureTestCase {
  public void testGenericMethod() {
    myFixture.configureByText("a.groovy", """
      
      class Bar<T> { java.util.List<T> foo(T param); }
      new Bar<String>().f<caret>oo();""");
    PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    assertEquals("""
                   <span style="color:#000000;">Bar</span>
                   <a href="psi_element://java.util.List"><code><span style="color:#0000ff;">List</span></code></a><span style="">&lt;</span><a href="psi_element://java.lang.String"><code><span style="color:#0000ff;">String</span></code></a><span style="">&gt;</span> <span style="color:#000000;">foo</span><span style="">(</span><a href="psi_element://java.lang.String"><code><span style="color:#0000ff;">String</span></code></a> <span style="color:#000000;">param</span><span style="">)</span>""",
                 CtrlMouseHandler.getInfo(ref.resolve(), ref.getElement()));
  }

  public void testGenericField() {
    myFixture.configureByText("a.groovy", """
      
      class Bar<T> { T field; }
      new Bar<Integer>().fi<caret>eld""");
    PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    assertEquals("""
                   <span style="color:#000000;">Bar</span>
                   <a href="psi_element://java.lang.Integer"><code><span style="color:#0000ff;">Integer</span></code></a> <span style="color:#000000;">getField</span><span style="">(</span><span style="">)</span>""",
                 CtrlMouseHandler.getInfo(ref.resolve(), ref.getElement()));
  }

  public void testLink() {
    doTest(
      """
        class Gr {
          /**
           * Use {@link #bar()} from class {@link Gr} instead
           */
          void foo() {}
          void bar() {}
        }
        new Gr().fo<caret>o()
        """,
      """
        <div class="bottom"><icon src="AllIcons.Nodes.Class">&nbsp;<a href="psi_element://Gr"><code><span style="color:#000000;">Gr</span></code></a></div><div class='definition'><pre><span style="color:#000043;font-weight:bold;">void</span>&nbsp;<span style="color:#000000;">foo</span><span style="">(</span><span style="">)</span></pre></div><div class='content'>
             Use <a href="psi_element://Gr#bar()"><code><span style="color:#0000ff;">bar</span><span style="">()</span></code></a> from class <a href="psi_element://Gr"><code><span style="color:#0000ff;">Gr</span></code></a> instead
           </div><table class='sections'></table>""");
  }

  public void test_link_with_label() {
    doTest("""
             /**
              * check this out {@link java.lang.CharSequence character sequences}
              */
             def docs() {}
             
             <caret>docs()
             """,
           """
             <div class="bottom"><icon src="AllIcons.Nodes.Class">&nbsp;<a href="psi_element://_"><code><span style="color:#000000;">_</span></code></a></div><div class='definition'><pre><a href="psi_element://java.lang.Object"><code><span style="color:#000000;">Object</span></code></a>&nbsp;<span style="color:#000000;">docs</span><span style="">(</span><span style="">)</span></pre></div><div class='content'>
                check this out <a href="psi_element://java.lang.CharSequence"><code><span style="color:#0000ff;">character sequences</span></code></a>
              </div><table class='sections'></table>""");
  }

  public void test_link_to_method() {
    doTest(
      """
        class Main {
          /**
           * Link 1: {@link #foo(String[])}\s
           * <p>
           * Link 2: {@link #bar(String[])}
           * <p>
           * Link 3: {@link #bar(String[], Integer)}
           */
          static void docs() {}
          void foo(String[] args) {}
          void bar(String[] args) {}
          void bar(String[] args, Integer i) {}
        }
        Main.<caret>docs()
        """,
      """
        <div class="bottom"><icon src="AllIcons.Nodes.Class">&nbsp;<a href="psi_element://Main"><code><span style="color:#000000;">Main</span></code></a></div><div class='definition'><pre><span style="color:#000043;font-weight:bold;">static</span>&nbsp;<span style="color:#000043;font-weight:bold;">void</span>&nbsp;<span style="color:#000000;">docs</span><span style="">(</span><span style="">)</span></pre></div><div class='content'>
             Link 1: <a href="psi_element://Main#foo(java.lang.String[])"><code><span style="color:#0000ff;">foo</span><span style="">(String[])</span></code></a>\s
             <p>
             Link 2: <a href="psi_element://Main#bar(java.lang.String[])"><code><span style="color:#0000ff;">bar</span><span style="">(String[])</span></code></a>
             <p>
             Link 3: <a href="psi_element://Main#bar(java.lang.String[], java.lang.Integer)"><code><span style="color:#0000ff;">bar</span><span style="">(String[],&#32;Integer)</span></code></a>
           </div><table class='sections'></table>""");
  }

  public void test_untyped_local_variable() {
    doTest("""
             def aa = 1
             a<caret>a
             """,
           "<div class='definition'><pre><a href=\"psi_element://java.lang.Object\"><code><span style=\"color:#0000ff;\">Object</span></code></a> <span style=\"color:#000000;\">aa</span></pre></div><table class='sections'></table><p style='padding-left:8px;'><span style=\"color: #909090\">[Inferred type]</span> <a href=\"psi_element://java.lang.Integer\"><code><span style=\"color:#0000ff;\">Integer</span></code></a>");
  }

  public void test_implicit_closure_parameter() {
    doTest("""
             List<String> ss = []
             ss.collect { i<caret>t }
             """,
           "<div class='definition'><pre><a href=\"psi_element://java.lang.Object\"><code><span style=\"color:#0000ff;\">Object</span></code></a> <span style=\"color:#000000;\">it</span></pre></div><p style='padding-left:8px;'><span style=\"color: #909090\">[Inferred type]</span> <a href=\"psi_element://java.lang.String\"><code><span style=\"color:#0000ff;\">String</span></code></a>");
  }

  public void test_code_tag() {
    doTest(
      """
        class Foo {
            /**
             * May return {@code null}
             */
            String foo() {
                null
            }
        }
        new Foo().<caret>foo()
        """,
      """
        <div class="bottom"><icon src="AllIcons.Nodes.Class">&nbsp;<a href="psi_element://Foo"><code><span style="color:#000000;">Foo</span></code></a></div><div class='definition'><pre><a href="psi_element://java.lang.String"><code><span style="color:#000000;">String</span></code></a>&nbsp;<span style="color:#000000;">foo</span><span style="">(</span><span style="">)</span></pre></div><div class='content'>
               May return <code><span style="color:#000043;font-weight:bold;">null</span></code>
             </div><table class='sections'></table>""");
  }

  public void test_IDEA_261068() {
    doTest("""
             
             /**
              * @return <code> lorem ipsum </code>
              */
             def foo() {}
             
             f<caret>oo()""",
           "<div class=\"bottom\"><icon src=\"AllIcons.Nodes.Class\">&nbsp;<a href=\"psi_element://_\"><code><span style=\"color:#000000;\">_</span></code></a></div><div class='definition'><pre><a href=\"psi_element://java.lang.Object\"><code><span style=\"color:#000000;\">Object</span></code></a>&nbsp;<span style=\"color:#000000;\">foo</span><span style=\"\">(</span><span style=\"\">)</span></pre></div><table class='sections'><p><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'><p><code> lorem ipsum </code></td></table>");
  }

  public void test_web_reference() {
    doTest("""
             
             /**
              * @see <a href="https://google.com">ref</a>
              */
             class GroovyDocTest<T> { }
             
             new Gr<caret>oovyDocTest<Integer>()""",
           "<div class='definition'><pre><span style=\"color:#000043;font-weight:bold;\">class</span> <span style=\"color:#000000;\">GroovyDocTest</span><span style=\"\">&lt;</span><span style=\"color:#20999d;\">T</span><span style=\"\">&gt;</span></pre></div><table class='sections'><p><tr><td valign='top' class='section'><p>See Also:</td><td valign='top'><p><a href=\"https://google.com\">ref</a></td></table>");
  }

  public void test_type_parameter_in_param() {
    doTest("""
             
             /**
              * @param <T> kej
              */
             class GroovyDocTest<T> { }
             
             new Gr<caret>oovyDocTest<Integer>()""",
           "<div class='definition'><pre><span style=\"color:#000043;font-weight:bold;\">class</span> <span style=\"color:#000000;\">GroovyDocTest</span><span style=\"\">&lt;</span><span style=\"color:#20999d;\">T</span><span style=\"\">&gt;</span></pre></div><table class='sections'><p><tr><td valign='top' class='section'><p>Type parameters:</td><td valign='top'><code>&lt;<span style=\"color:#20999d;\">T</span>&gt;</code> &ndash;  kej</td></table>");
  }

  public void test_render_multiline_inline_code_tag() {
    doTest("""
             
             /**
              * {@code
              * aaa
              * bbb
              * ccc
              * }
              */
             def foo() {}
             fo<caret>o()
             """,
           """
             <div class="bottom"><icon src="AllIcons.Nodes.Class">&nbsp;<a href="psi_element://_"><code><span style="color:#000000;">_</span></code></a></div><div class='definition'><pre><a href="psi_element://java.lang.Object"><code><span style="color:#000000;">Object</span></code></a>&nbsp;<span style="color:#000000;">foo</span><span style="">(</span><span style="">)</span></pre></div><div class='content'>
                <code><span style="">aaa&#32;bbb&#32;ccc</span></code>
              </div><table class='sections'></table>""");
  }

  private void doTest(String text, @Language("HTML") String doc) {
    myFixture.configureByText("_.groovy", text);
    PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
    GroovyDocumentationProvider provider = new GroovyDocumentationProvider();
    String info = provider.generateDoc(ref.resolve(), ref.getElement());
    TestCase.assertEquals(doc, info);
  }
}
