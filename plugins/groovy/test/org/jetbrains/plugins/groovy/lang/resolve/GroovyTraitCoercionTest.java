// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.lang.resolve.GroovyResolveFixtureKt.groovyResolveFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class GroovyTraitCoercionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<GroovyResolveFixture> resolve = groovyResolveFixture(projectFixture);

  @BeforeEach
  void setUp() {
    addNecessaryClasses();
  }

  public void addNecessaryClasses() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.addFileToProject("classes.groovy", """
      trait T1 {
          String foo() {}
          def bar() {}
      }
      
      trait T2 {
          Integer foo() {}
      }
      
      interface I {
          def someMethod()
      }
      
      class Foo implements I {
          Foo foo() {}
          def someMethod() {}
          def fooMethod() {}
      }
      """);
  }

  @Test
  public void testTypesAsAndChainedAs() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
    map.put("new Foo() as T1", "Foo as T1");
    map.put("new Foo() as T2", "Foo as T2");
    map.put("(new Foo() as T1) as T2", "Foo as T1, T2");
    map.put("(new Foo() as T2) as T1", "Foo as T2, T1");
    doTestExpressionTypes(map);
  }

  @Test
  public void testTypesWithTraitsAndChainedWithTraits() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
    map.put("new Foo().withTraits(T1)", "Foo as T1");
    map.put("new Foo().withTraits(T2)", "Foo as T2");
    map.put("new Foo().withTraits(T1).withTraits(T2)", "Foo as T1, T2");
    map.put("new Foo().withTraits(T2).withTraits(T1)", "Foo as T2, T1");
    doTestExpressionTypes(map);
  }

  @Test
  public void testTypesRemoveDuplicates() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
    map.put("(new Foo() as T1) as T1", "Foo as T1");
    map.put("(new Foo() as T1).withTraits(T1)", "Foo as T1");
    map.put("new Foo().withTraits(T2) as T2", "Foo as T2");
    map.put("new Foo().withTraits(T2).withTraits(T2)", "Foo as T2");
    doTestExpressionTypes(map);
  }

  @Test
  public void testTypesMixedAsAndWithTraits() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
    map.put("(new Foo() as T1).withTraits(T2)", "Foo as T1, T2");
    map.put("(new Foo() as T2).withTraits(T1)", "Foo as T2, T1");
    map.put("new Foo().withTraits(T1) as T2", "Foo as T1, T2");
    map.put("new Foo().withTraits(T2) as T1", "Foo as T2, T1");
    doTestExpressionTypes(map);
  }

  @Test
  public void testTypesWithTwoTraits() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
    map.put("new Foo().withTraits(T1, T2)", "Foo as T1, T2");
    doTestExpressionTypes(map);
  }

  @Test
  public void testTypesTraitsDuplicatesAndOrder() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
    map.put("(new Foo() as T1).withTraits(T2, T1)", "Foo as T2, T1");
    doTestExpressionTypes(map);
  }

  @Test
  public void testAsOperator() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(6);
    map.put("(new Foo() as T1).fo<caret>o()", "T1");
    map.put("(new Foo() as T1).ba<caret>r()", "T1");
    map.put("(new Foo() as T1).some<caret>Method()", "Foo");
    map.put("(new Foo() as T1).foo<caret>Method()", "Foo");
    map.put("((new Foo() as T1) as T2).fo<caret>o()", "T2");
    map.put("((new Foo() as T1) as T2).ba<caret>r()", "T1");
    doTestResolveContainingClass(map);
  }

  @Test
  public void testWithTraits() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
    map.put("new Foo().withTraits(T1).fo<caret>o()", "T1");
    map.put("new Foo().withTraits(T1).ba<caret>r()", "T1");
    map.put("new Foo().withTraits(T1).withTraits(T2).fo<caret>o()", "T2");
    map.put("new Foo().withTraits(T2).withTraits(T1).fo<caret>o()", "T1");
    doTestResolveContainingClass(map);
  }

  @Test
  public void testDuplicatesAndOrder() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
    map.put("(new Foo() as T1).withTraits(T2, T1).fo<caret>o()", "T1");
    doTestResolveContainingClass(map);
  }

  @Test
  public void testCompletionPriority() {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
      (new Foo().withTraits(T1, T2).f<caret>)
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      final PsiMethod method = myFixture.findClass("T2").findMethodsByName("foo", false)[0];
      LookupElement[] lookupElements = myFixture.complete(CompletionType.BASIC);
      assertTrue(ContainerUtil.exists(lookupElements, it -> it.getPsiElement().equals(method)));
    });
  }

  @SuppressWarnings("unchecked")
  public <T extends GrExpression> T configureByExpression(String text, Class<T> expressionType) {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    assertNotNull(text);
    myFixture.configureByText(GroovyFileType.GROOVY_FILE_TYPE, text);
    return ReadAction.computeBlocking(() -> {
      return (T)PsiTreeUtil.findFirstParent(myFixture.getFile().findElementAt(0),
                                            element -> expressionType.isInstance(element) && element.getText().equals(text));
    });
  }

  @SuppressWarnings("unchecked")
  public <T extends GrExpression> T configureByExpression(String text) {
    return (T)configureByExpression(text, GrExpression.class);
  }

  public void doTestExpressionType(String expressionString, final String typeString) {
    GrExpression expression = configureByExpression(expressionString);
    ReadAction.runBlocking(() -> {
      assertNotNull(expression.getType());
      assertEquals(expression.getType().getInternalCanonicalText(), typeString);
    });
  }

  public void doTestExpressionTypes(Map<String, String> data) {
    for (Map.Entry<String, String> entry : data.entrySet()) {
      doTestExpressionType(entry.getKey(), entry.getValue());
    }
  }

  public void doTestResolveContainingClass(Map<String, String> data) {
    LightJavaCodeInsightTestFixture myFixture = resolve.get().getFixture();
    for (Map.Entry<String, String> entry : data.entrySet()) {
      GrMethod method = resolve.get().resolveByText(entry.getKey(), GrMethod.class);
      ReadAction.runBlocking(() -> assertEquals(method.getContainingClass(), myFixture.findClass(entry.getValue())));
    }
  }
}
