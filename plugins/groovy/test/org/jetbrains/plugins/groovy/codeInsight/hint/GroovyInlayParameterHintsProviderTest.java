// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeInsight.hint;

import com.intellij.codeInsight.daemon.impl.ParameterHintsPresentationManager;
import com.intellij.codeInsight.hints.settings.ParameterNameHintsSettings;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Inlay;
import com.intellij.testFramework.LightProjectDescriptor;
import junit.framework.TestCase;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;

import java.util.List;

public class GroovyInlayParameterHintsProviderTest extends LightGroovyTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    getFixture().addFileToProject("Foo.groovy", """
      class Foo {
        Foo() {}
        Foo(a, b, c) {}
        void simple(a) {}
        void simple(a, b) {}
        void defaultArgs(a, b = 1,c) {}
        void mapArgs(Map m, a) {}
        void varArgs(a, def... b) {}
        void combo(Map m, a, b = 1, def ... c) {}
        def getAt(a) {}
      }
      """);
  }

  @Override
  public void tearDown() throws Exception {
    ParameterNameHintsSettings.getInstance().loadState(new Element("element"));
    super.tearDown();
  }

  private void testInlays(String text) {
    getFixture().configureByText("_.groovy", text.replaceAll("<hint name=\"([.\\w]*)\">", ""));
    checkResult(text.replaceAll("<caret>", ""));
  }

  private void checkResult(String expected) {
    getFixture().doHighlighting();

    final ParameterHintsPresentationManager manager = ParameterHintsPresentationManager.getInstance();
    List<Inlay<?>> inlays = manager.getParameterHintsInRange(getEditor(), 0, getEditor().getDocument().getTextLength());

    final StringBuilder actualText = new StringBuilder(getFixture().getFile().getText());
    int offset = 0;
    for (Inlay<?> inlay : inlays) {
      String hintName = manager.getHintText(inlay);
      if (hintName == null) continue;
      String hintText = hintName.substring(0, hintName.length() - 1);
      String hintString = "<hint name=\"" + hintText + "\">";
      actualText.insert(inlay.getOffset() + offset, hintString);
      offset += hintString.length();
    }
    TestCase.assertEquals(expected, actualText.toString());
  }

  public void test_method_call_expressions() {
    testInlays("""
                 def aa = new Object()
                 def foo = new Foo()
                 foo.with {
                   simple(<hint name="a">null)
                   simple(aa)
                   simple(<hint name="a">-1, <hint name="b">+2)
                   simple(<hint name="a">++1, <hint name="b">2++)
                   simple(<hint name="a">--1, <hint name="b">2--)
                   simple(<hint name="a">~1, -"foo")
                   defaultArgs(<hint name="a">1, <hint name="c">2)
                   defaultArgs(<hint name="a">1, <hint name="b">2, <hint name="c">3)
                   mapArgs(foo: 1, <hint name="a">null, bar: 2)
                   varArgs(<hint name="a">1, <hint name="b">null)        // 'null' passed as is
                   varArgs(<hint name="a">1, <hint name="...b">null, null)  // 'null'-s wrapped into array
                   varArgs(<hint name="a">1, <hint name="...b">2)
                   varArgs(<hint name="a">1, <hint name="...b">2, 3, 4)
                   varArgs(<hint name="a">1, <hint name="...b">2, aa)
                   varArgs(<hint name="a">1, <hint name="...b">aa, 3)
                   varArgs(<hint name="a">1, aa, aa)
                   combo(<hint name="a">1, foo: 10, <hint name="b">2, <hint name="...c">3, 4, bar: 20, 5)
                 }
                 """);
  }

  public void test_method_call_applications() {
    testInlays("""
                 def aa = new Object()
                 def foo = new Foo()
                 foo.with {
                   simple <hint name="a">null
                   simple aa
                   defaultArgs <hint name="a">1, <hint name="c">2
                   defaultArgs <hint name="a">1, <hint name="b">2, <hint name="c">3
                   mapArgs foo: 1, <hint name="a">null, bar: 2
                   varArgs <hint name="a">1, <hint name="...b">2
                   varArgs <hint name="a">1, <hint name="...b">2, 3, 4
                   varArgs <hint name="a">1, <hint name="...b">2, aa
                   varArgs <hint name="a">1, <hint name="...b">aa, 3
                   varArgs <hint name="a">1, aa, aa
                   combo <hint name="a">1, foo: 10, <hint name="b">2, <hint name="...c">3, 4, bar: 20, 5
                 }
                 """);
  }

  public void test_new_expression() {
    testInlays("new Foo(<hint name=\"a\">1, <hint name=\"b\">2, <hint name=\"c\">4)");
  }

  public void test_constructor_invocation() {
    testInlays("""
                 class Bar {
                   Bar() {
                     this(<hint name="a">1, <hint name="b">4, <hint name="c">5)
                   }
                 
                   Bar(a, b, c) {}
                 }
                 """);
  }

  public void test_enum_constants() {
    testInlays("""
                 enum Baz {
                   ONE(<hint name="a">1, <hint name="b">2),
                   TWO(<hint name="a">4, <hint name="b">3)
                   Baz(a, b) {}
                 }
                 """);
  }

  public void test_no_DGM_inlays() {
    testInlays("[].each {}");
  }

  public void test_closure_arguments() {
    testInlays("""
                 new Foo().with {
                   simple {}
                   simple(<hint name="a">{})
                   simple <hint name="a">null, <hint name="b">{}
                 }
                 """);
  }

  public void test_lambda_arguments() {
    testInlays("""
                 new Foo().with {
                   simple {}
                   simple(<hint name="a">()->{})
                   simple <hint name="a">null, <hint name="b">()->{}
                 }
                 """);
  }

  public void test_method_with_default_arguments_blacklist() {
    testInlays("""
                 new Foo().with {
                    defaultArgs(<hint name="a">1, <hint name="c">2)
                    defaultArgs(aa, <hint name="b"><caret>2, <hint name="c">3)
                 }
                 """);
    IntentionAction intention = getFixture().getAvailableIntention("Do not show hints for current method");
    assertNotNull(intention);
    getFixture().launchAction(intention);
    checkResult("""
                  new Foo().with {
                     defaultArgs(1, 2)
                     defaultArgs(aa, 2, 3)
                  }
                  """);
  }

  @Override
  public final @NotNull LightProjectDescriptor getProjectDescriptor() {
    return GroovyProjectDescriptors.GROOVY_LATEST;
  }
}
