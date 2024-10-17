// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.LightGroovyTestCase;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;

import java.net.URL;

public class DsldTest extends LightGroovyTestCase {
  public void testUnknownPointcut() {
    checkHighlighting("contribute(asdfsadf()) { property name:'foo', type:'String' }", "println foo.substring(2) + <warning>bar</warning>");
  }

  public void testCurrentType() {
    checkHighlighting("contribute(currentType(\"java.lang.String\")) { property name:\"foo\" }",
                      "println \"\".foo + [].<warning>foo</warning>");
  }

  public void testSubType() {
    checkHighlighting("contribute(currentType(subType(\"java.lang.String\"))) { property name:\"foo\" }",
                      "println \"\".foo + [].<warning>foo</warning>");
  }

  public void testAnd() {
    checkHighlighting("contribute(currentType(subType(\"java.lang.Runnable\") & name(\"Foo\"))) { property name:\"foo\" }", """
      class Foo implements Runnable {
        void run() { println foo }
      }
      class Bar implements Runnable {
        void run() { println <warning>foo</warning> }
        class Foo {
         void run() { println <warning>foo</warning> }
        }
      }
      println <warning>foo</warning>
      """);
  }

  public void testOr() {
    checkHighlighting("contribute(currentType(subType(\"MyRunnable\") | name(\"Foo\"))) { property name:\"foo\" }", """
      interface MyRunnable {}
      class Foo implements MyRunnable {
       void run() { println foo }
      }
      class Bar implements MyRunnable {
       void run() { println foo }
       static class Foo {
         void run() { println foo }
       }
      }
      println <warning>foo</warning>
      """);
  }

  public void testNot() {
    checkHighlighting("contribute(currentType(~name(\"Foo\"))) { property name:\"foo\" }", """
      class Foo {
       void run() { println <warning>foo</warning> }
      }
      class Bar extends Foo {
       void run() { println foo }
      }
      """);
  }

  public void testTypeName() {
    checkHighlighting("contribute(currentType(name(\"Foo\"))) { property name:\"foo\" }", """
      class Foo {}
      class Bar extends Foo {}
      println new Foo().foo + new Bar().<warning>foo</warning>
      """);
  }

  public void testBind() {
    checkHighlighting("contribute(bind(types:currentType(\"java.lang.CharSequence\"))) { property name:types[0].name[-3..-1] }",
                      "println \"\".ing + \"\".<warning>foo</warning>");
  }

  public void testImplicitBind() {
    checkHighlighting("contribute(types:currentType(\"java.lang.CharSequence\")) { property name:types[0].name[-3..-1] }",
                      "println \"\".ing + \"\".<warning>foo</warning>");
  }

  public void testEnclosingType() {
    checkHighlighting("contribute(enclosingType(\"Foo\")) { property name:\"foo\" }", """
      class Foo {
        def goo() { println foo + "".foo }
      }
      class Bar extends Foo {
        def goo() { println <warning>foo</warning> }
      }
      println new Foo().<warning>foo</warning>
      println <warning>foo</warning>
      """);
  }

  public void testEnclosingMethod() {
    checkHighlighting("contribute(enclosingMethod(\"goo\")) { property name:\"foo\" }", """
      class Foo {
        def goo() { println foo + "".foo }
        def doo() { println <warning>foo</warning> }
      }
      """);
  }

  public void testMethodName() {
    checkHighlighting("contribute(enclosingMethod(name(\"goo\"))) { property name:\"foo\" }", """
      def goo() { println foo + "".foo }
      def doo() { println <warning>foo</warning> }
      """);
  }

  public void testSupportsVersion() {
    checkHighlighting("""
                        if (supportsVersion(intellij:'9.0')) {
                          contribute(currentType("java.lang.String")) { property name:"foo" }
                        } else {
                          contribute(currentType("java.lang.String")) { property name:"bar" }
                        }
                        
                        if (!supportsVersion(groovyEclipse:'9.0')) {
                          contribute(currentType("java.lang.String")) { property name:"goo" }
                        }
                        """, "println \"\".foo + \"\".<warning>bar</warning> + \"\".goo");
  }

  public void testAssertVersion() {
    checkHighlighting("""
                        assertVersion dsl:'1.0'
                        contribute(currentType("java.lang.String")) { property name:"foo" }
                        """, "println \"\".foo");
  }

  public void testAssertVersionDsl() {
    checkHighlighting("""
                        assertVersion intellij:'9.0'
                        contribute(currentType("java.lang.String")) { property name:"foo" }
                        """, "println \"\".foo");
  }

  public void testAssertVersionFail() {
    checkHighlighting("""
                        assertVersion intellij:'23942.0'
                        contribute(currentType("java.lang.String")) { property name:"foo" }
                        """, "println \"\".<warning>foo</warning>");
  }

  public void testAssertVersionFailDsl() {
    checkHighlighting("""
                        assertVersion dsl:'239.0'
                        contribute(currentType("java.lang.String")) { property name:"foo" }
                        """, "println \"\".<warning>foo</warning>");
  }

  public void testAddConstructor() {
    addDsld("contribute(currentType(\"java.lang.String\")) { constructor params:[foo:Integer, bar:Integer, goo:Integer] }");

    myFixture.configureByText("a.groovy", "new Stri<caret>ng(2,3,9)");
    GrNewExpression newExpr =
      PsiTreeUtil.findElementOfClassAtOffset(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), GrNewExpression.class,
                                             false);
    PsiMethod method = newExpr.resolveMethod();
    assert method.isConstructor();
    assert method.getParameterList().getParameters().length == 3;
  }

  public void testMeta() {
    URL dslUrl = GdslScriptProvider.class.getClassLoader().getResource("standardDsls/metaDsl.gdsl");
    VirtualFile dslVirtualFile = myFixture.copyFileToProject(dslUrl.getPath(), "metaDsl.gdsl");
    myFixture.configureFromExistingVirtualFile(dslVirtualFile);
    GroovyDslFileIndex.activate(dslVirtualFile);
    myFixture.enableInspections(new GrUnresolvedAccessInspection());
    myFixture.checkHighlighting();
  }

  private void checkHighlighting(String dsl, String code) {
    addDsld(dsl);

    checkHighlighting(code);
  }

  private void addDsld(String dsl) {
    PsiFile file = myFixture.addFileToProject("a.gdsl", dsl);
    GroovyDslFileIndex.activate(file.getVirtualFile());
  }

  private void checkHighlighting(String text) {
    myFixture.configureByText("a.groovy", text);
    myFixture.enableInspections(new GrUnresolvedAccessInspection());
    myFixture.checkHighlighting(true, false, false);
  }
}
