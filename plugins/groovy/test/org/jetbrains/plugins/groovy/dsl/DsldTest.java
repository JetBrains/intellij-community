// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.bazel.runfiles.BazelLabel;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.common.BazelTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class DsldTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_1);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @Test
  public void testUnknownPointcut() {
    checkHighlighting("contribute(asdfsadf()) { property name:'foo', type:'String' }", "println foo.substring(2) + <warning>bar</warning>");
  }

  @Test
  public void testCurrentType() {
    checkHighlighting("contribute(currentType(\"java.lang.String\")) { property name:\"foo\" }",
                      "println \"\".foo + [].<warning>foo</warning>");
  }

  @Test
  public void testSubType() {
    checkHighlighting("contribute(currentType(subType(\"java.lang.String\"))) { property name:\"foo\" }",
                      "println \"\".foo + [].<warning>foo</warning>");
  }

  @Test
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

  @Test
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

  @Test
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

  @Test
  public void testTypeName() {
    checkHighlighting("contribute(currentType(name(\"Foo\"))) { property name:\"foo\" }", """
      class Foo {}
      class Bar extends Foo {}
      println new Foo().foo + new Bar().<warning>foo</warning>
      """);
  }

  @Test
  public void testBind() {
    checkHighlighting("contribute(bind(types:currentType(\"java.lang.CharSequence\"))) { property name:types[0].name[-3..-1] }",
                      "println \"\".ing + \"\".<warning>foo</warning>");
  }

  @Test
  public void testImplicitBind() {
    checkHighlighting("contribute(types:currentType(\"java.lang.CharSequence\")) { property name:types[0].name[-3..-1] }",
                      "println \"\".ing + \"\".<warning>foo</warning>");
  }

  @Test
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

  @Test
  public void testEnclosingMethod() {
    checkHighlighting("contribute(enclosingMethod(\"goo\")) { property name:\"foo\" }", """
      class Foo {
        def goo() { println foo + "".foo }
        def doo() { println <warning>foo</warning> }
      }
      """);
  }

  @Test
  public void testMethodName() {
    checkHighlighting("contribute(enclosingMethod(name(\"goo\"))) { property name:\"foo\" }", """
      def goo() { println foo + "".foo }
      def doo() { println <warning>foo</warning> }
      """);
  }

  @Test
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

  @Test
  public void testAssertVersion() {
    checkHighlighting("""
                        assertVersion dsl:'1.0'
                        contribute(currentType("java.lang.String")) { property name:"foo" }
                        """, "println \"\".foo");
  }

  @Test
  public void testAssertVersionDsl() {
    checkHighlighting("""
                        assertVersion intellij:'9.0'
                        contribute(currentType("java.lang.String")) { property name:"foo" }
                        """, "println \"\".foo");
  }

  @Test
  public void testAssertVersionFail() {
    checkHighlighting("""
                        assertVersion intellij:'23942.0'
                        contribute(currentType("java.lang.String")) { property name:"foo" }
                        """, "println \"\".<warning>foo</warning>");
  }

  @Test
  public void testAssertVersionFailDsl() {
    checkHighlighting("""
                        assertVersion dsl:'239.0'
                        contribute(currentType("java.lang.String")) { property name:"foo" }
                        """, "println \"\".<warning>foo</warning>");
  }

  @Test
  public void testAddConstructor() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    addDsld("contribute(currentType(\"java.lang.String\")) { constructor params:[foo:Integer, bar:Integer, goo:Integer] }");

    myFixture.configureByText("a.groovy", "new Stri<caret>ng(2,3,9)");
    ReadAction.runBlocking(() -> {
      GrNewExpression newExpr =
        PsiTreeUtil.findElementOfClassAtOffset(myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), GrNewExpression.class,
                                               false);
      PsiMethod method = newExpr.resolveMethod();
      assertTrue(method.isConstructor());
      assertThat(method.getParameterList().getParameters()).hasSize(3);
    });
  }

  @Test
  public void testMeta() throws IOException {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    var path = BazelTestUtil.isUnderBazelTest()
               ? BazelTestUtil.getFileFromBazelRuntime(
                 BazelLabel.Companion.fromString("@community//plugins/groovy/groovy-psi:standardDsls/metaDsl.gdsl"))
               : Path.of(PathManager.getCommunityHomePath(), "plugins/groovy/groovy-psi/standardDsls/metaDsl.gdsl");
    String content = Files.readString(path);
    VirtualFile dslVirtualFile = myFixture.createFile("metaDsl.gdsl", content);
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
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    PsiFile file = myFixture.addFileToProject("a.gdsl", dsl);
    GroovyDslFileIndex.activate(file.getVirtualFile());
  }

  private void checkHighlighting(String text) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.groovy", text);
    myFixture.enableInspections(new GrUnresolvedAccessInspection());
    myFixture.checkHighlighting(true, false, false);
  }
}
