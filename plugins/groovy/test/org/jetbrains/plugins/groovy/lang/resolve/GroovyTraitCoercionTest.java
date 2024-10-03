// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.junit.Assert;

import java.util.LinkedHashMap;
import java.util.Map;

public class GroovyTraitCoercionTest extends GroovyResolveTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    addNecessaryClasses();
  }

  public void addNecessaryClasses() {
    getFixture().addFileToProject("classes.groovy", """
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

  public void testTypesAsAndChainedAs() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
    map.put("new Foo() as T1", "Foo as T1");
    map.put("new Foo() as T2", "Foo as T2");
    map.put("(new Foo() as T1) as T2", "Foo as T1, T2");
    map.put("(new Foo() as T2) as T1", "Foo as T2, T1");
    doTestExpressionTypes(map);
  }

  public void testTypesWithTraitsAndChainedWithTraits() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
    map.put("new Foo().withTraits(T1)", "Foo as T1");
    map.put("new Foo().withTraits(T2)", "Foo as T2");
    map.put("new Foo().withTraits(T1).withTraits(T2)", "Foo as T1, T2");
    map.put("new Foo().withTraits(T2).withTraits(T1)", "Foo as T2, T1");
    doTestExpressionTypes(map);
  }

  public void testTypesRemoveDuplicates() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
    map.put("(new Foo() as T1) as T1", "Foo as T1");
    map.put("(new Foo() as T1).withTraits(T1)", "Foo as T1");
    map.put("new Foo().withTraits(T2) as T2", "Foo as T2");
    map.put("new Foo().withTraits(T2).withTraits(T2)", "Foo as T2");
    doTestExpressionTypes(map);
  }

  public void testTypesMixedAsAndWithTraits() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
    map.put("(new Foo() as T1).withTraits(T2)", "Foo as T1, T2");
    map.put("(new Foo() as T2).withTraits(T1)", "Foo as T2, T1");
    map.put("new Foo().withTraits(T1) as T2", "Foo as T1, T2");
    map.put("new Foo().withTraits(T2) as T1", "Foo as T2, T1");
    doTestExpressionTypes(map);
  }

  public void testTypesWithTwoTraits() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
    map.put("new Foo().withTraits(T1, T2)", "Foo as T1, T2");
    doTestExpressionTypes(map);
  }

  public void testTypesTraitsDuplicatesAndOrder() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
    map.put("(new Foo() as T1).withTraits(T2, T1)", "Foo as T2, T1");
    doTestExpressionTypes(map);
  }

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

  public void testWithTraits() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(4);
    map.put("new Foo().withTraits(T1).fo<caret>o()", "T1");
    map.put("new Foo().withTraits(T1).ba<caret>r()", "T1");
    map.put("new Foo().withTraits(T1).withTraits(T2).fo<caret>o()", "T2");
    map.put("new Foo().withTraits(T2).withTraits(T1).fo<caret>o()", "T1");
    doTestResolveContainingClass(map);
  }

  public void testDuplicatesAndOrder() {
    LinkedHashMap<String, String> map = new LinkedHashMap<>(1);
    map.put("(new Foo() as T1).withTraits(T2, T1).fo<caret>o()", "T1");
    doTestResolveContainingClass(map);
  }

  public void testCompletionPriority() {
    getFixture().configureByText(GroovyFileType.GROOVY_FILE_TYPE, """
      (new Foo().withTraits(T1, T2).f<caret>)
      """);
    final PsiMethod method = getFixture().findClass("T2").findMethodsByName("foo", false)[0];
    LookupElement[] lookupElements = getFixture().complete(CompletionType.BASIC);
    Assert.assertTrue(ContainerUtil.exists(lookupElements, it -> it.getPsiElement().equals(method)));
  }

  @SuppressWarnings("unchecked")
  public <T extends GrExpression> T configureByExpression(String text, Class<T> expressionType) {
    Assert.assertNotNull(text);
    getFixture().configureByText(GroovyFileType.GROOVY_FILE_TYPE, text);
    return (T)PsiTreeUtil.findFirstParent(getFixture().getFile().findElementAt(0),
                                          element -> expressionType.isInstance(element) && element.getText().equals(text));
  }

  @SuppressWarnings("unchecked")
  public <T extends GrExpression> T configureByExpression(String text) {
    return (T)configureByExpression(text, GrExpression.class);
  }

  public void doTestExpressionType(String expressionString, final String typeString) {
    GrExpression expression = configureByExpression(expressionString);
    Assert.assertNotNull(expression.getType());
    Assert.assertEquals(expression.getType().getInternalCanonicalText(), typeString);
  }

  public void doTestExpressionTypes(Map<String, String> data) {
    for (Map.Entry<String, String> entry : data.entrySet()) {
      doTestExpressionType(entry.getKey(), entry.getValue());
    }
  }

  public void doTestResolveContainingClass(Map<String, String> data) {
    for (Map.Entry<String, String> entry : data.entrySet()) {
      Assert.assertEquals(resolveByText(entry.getKey(), GrMethod.class).getContainingClass(), getFixture().findClass(entry.getValue()));
    }
  }

  @Override
  public @NotNull LightProjectDescriptor getProjectDescriptor() {
    return projectDescriptor;
  }

  public void setProjectDescriptor(LightProjectDescriptor projectDescriptor) {
    this.projectDescriptor = projectDescriptor;
  }

  private LightProjectDescriptor projectDescriptor = GroovyProjectDescriptors.GROOVY_LATEST;
}
