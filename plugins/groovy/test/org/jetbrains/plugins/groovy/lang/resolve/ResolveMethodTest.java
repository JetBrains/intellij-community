// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.groovy.testFramework.GroovyAssertions;
import com.intellij.groovy.testFramework.ResolveTest;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEllipsisType;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyReference;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrGdkMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrReflectedMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.DefaultConstructor;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrGdkMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrTraitMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass;
import org.jetbrains.plugins.groovy.lang.resolve.references.GrOperatorReference;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class ResolveMethodTest implements ResolveTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_5_0);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = lightJavaCodeInsightFixture(project);

  @BeforeEach
  void setUpTestDataPath() {
    groovy.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "resolve/method");
  }

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  public String getTestName() {
    return groovy.get().getTestName(false);
  }

  private @NotNull PsiReference configureByFile(@NonNls String filePath) {
    getFixture().configureByFile(getTestName() + "/" + filePath);
    return referenceUnderCaret(PsiReference.class);
  }

  private @Nullable PsiElement resolve(String fileName) {
    return EdtTestUtil.runInEdtAndGet(() -> configureByFile(fileName).resolve());
  }

  @Test
  public void staticImport3() {
    getFixture().copyFileToProject(getTestName() + "/org/Shrimp.groovy", "org/Shrimp.groovy");
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrMethod);
      Assertions.assertEquals(1, ((GrMethod)resolved).getParameters().length);
      Assertions.assertEquals("isShrimp", ((GrMethod)resolved).getName());
    });
  }

  @Test
  public void staticImport() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Disabled("static imports with non fully qualified names are not supported")
  @Test
  public void importStaticReverse() {
    PsiReference ref = configureByFile("ImportStaticReverse.groovy");
    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void simple() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrMethod);
      Assertions.assertEquals(1, ((GrMethod)resolved).getParameters().length);
    });
  }

  @Test
  public void varargs() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrMethod);
      Assertions.assertEquals(1, ((GrMethod)resolved).getParameters().length);
    });
  }

  @Test
  public void byName() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrMethod);
    });
  }

  @Test
  public void byName1() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrMethod);
      Assertions.assertEquals(2, ((GrMethod)resolved).getParameters().length);
    });
  }

  @Test
  public void byNameVarargs() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrMethod);
      Assertions.assertEquals(1, ((GrMethod)resolved).getParameters().length);
    });
  }

  @Test
  public void parametersNumber() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrMethod);
      Assertions.assertEquals(2, ((GrMethod)resolved).getParameters().length);
    });
  }

  @Test
  public void filterBase() {
    runInEdtAndWait(() -> {
      GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").getElement();
      Assertions.assertNotNull(ref.resolve());
      Assertions.assertEquals(1, ref.multiResolve(false).length);
    });
  }

  @Test
  public void twoCandidates() {
    runInEdtAndWait(() -> {
      GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").getElement();
      Assertions.assertNull(ref.resolve());
      Assertions.assertEquals(2, ref.multiResolve(false).length);
    });
  }

  @Test
  public void defaultMethod1() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrGdkMethod);
    });
  }

  @Test
  public void defaultStaticMethod() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrGdkMethodImpl);
      Assertions.assertTrue(((GrGdkMethodImpl)resolved).hasModifierProperty(PsiModifier.STATIC));
    });
  }

  @Test
  public void primitiveSubtyping() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrGdkMethodImpl);
      Assertions.assertTrue(((GrGdkMethodImpl)resolved).hasModifierProperty(PsiModifier.STATIC));
    });
  }

  @Test
  public void defaultMethod2() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
      Assertions.assertTrue(resolved instanceof GrGdkMethod);
    });
  }

  @Test
  public void grvy111() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
      Assertions.assertTrue(resolved instanceof GrGdkMethod);
      Assertions.assertEquals(0, ((PsiMethod)resolved).getParameterList().getParametersCount());
      Assertions.assertTrue(((PsiMethod)resolved).hasModifierProperty(PsiModifier.PUBLIC));
    });
  }

  @Test
  public void scriptMethod() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
      Assertions.assertEquals("groovy.lang.Script", ((PsiMethod)resolved).getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void arrayDefault() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
      Assertions.assertTrue(resolved instanceof GrGdkMethod);
    });
  }

  @Test
  public void arrayDefault1() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
      Assertions.assertTrue(resolved instanceof GrGdkMethod);
    });
  }

  @Test
  public void arrayDefault2() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
      Assertions.assertTrue(resolved instanceof GrGdkMethod);
    });
  }

  @Test
  public void spreadOperator() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
      GrMethodCallExpression methodCall = (GrMethodCallExpression)ref.getElement().getParent();
      PsiType type = methodCall.getType();
      Assertions.assertTrue(type instanceof PsiClassType);
      PsiClass clazz = ((PsiClassType)type).resolve();
      Assertions.assertNotNull(clazz);
      Assertions.assertEquals("java.util.ArrayList", clazz.getQualifiedName());
    });
  }

  @Test
  public void langClass() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
      Assertions.assertEquals("java.lang.Class", ((PsiMethod)resolved).getContainingClass().getQualifiedName());
    });
  }

  @Test
  public void complexOverload() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Test
  public void fromGetter() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Test
  public void overload1() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
      Assertions.assertEquals("java.io.Serializable", ((PsiMethod)resolved).getParameterList().getParameters()[0].getType().getCanonicalText());
    });
  }

  @Test
  public void constructor() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiMethod resolved = ((GrNewExpression)ref.getElement().getParent()).resolveMethod();
      Assertions.assertNotNull(resolved);
      Assertions.assertTrue(resolved.isConstructor());
    });
  }

  @Test
  public void constructor1() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiMethod method = ((GrNewExpression)ref.getElement().getParent()).resolveMethod();
      Assertions.assertNotNull(method);
      Assertions.assertTrue(method.isConstructor());
      Assertions.assertEquals(0, method.getParameterList().getParameters().length);
    });
  }

  @Test
  public void constructor2() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiMethod method = ((GrNewExpression)ref.getElement().getParent()).resolveMethod();
      Assertions.assertNull(method);
    });
  }

  @Test
  public void constructor3() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiMethod method = ((GrNewExpression)ref.getElement().getParent()).resolveMethod();
      Assertions.assertNotNull(method);
      Assertions.assertTrue(method.isConstructor());
      Assertions.assertEquals(0, method.getParameterList().getParameters().length);
    });
  }

  @Test
  public void wrongConstructor() {
    getFixture().addFileToProject("Classes.groovy", "class Foo { int a; int b }");
    GroovyReference ref = referenceByText("new Fo<caret>o(2, 3)");
    ReadAction.run(() -> Assertions.assertTrue(((GrNewExpression)ref.getElement().getParent()).advancedResolve().getElement() instanceof DefaultConstructor));
  }

  @Test
  public void langImmutableConstructor() {
    getFixture().addFileToProject("Classes.groovy", "@Immutable class Foo { int a; int b }");
    GroovyReference ref = referenceByText("new Fo<caret>o(2, 3)");
    ReadAction.run(() -> Assertions.assertTrue(((GrNewExpression)ref.getElement().getParent()).advancedResolve().getElement() instanceof PsiMethod));
  }

  @Test
  public void transformImmutableConstructor() {
    getFixture().addFileToProject("Classes.groovy", "@groovy.transform.Immutable class Foo { int a; int b }");
    GroovyReference ref = referenceByText("new Fo<caret>o(2, 3)");
    ReadAction.run(() -> Assertions.assertTrue(((GrNewExpression)ref.getElement().getParent()).advancedResolve().getElement() instanceof PsiMethod));
  }

  @Test
  public void tupleConstructor() {
    getFixture().addFileToProject("Classes.groovy", "@groovy.transform.TupleConstructor class Foo { int a; final int b }");
    GroovyReference ref = referenceByText("new Fo<caret>o(2, 3)");
    ReadAction.run(() -> {
      PsiElement target = ((GrNewExpression)ref.getElement().getParent()).advancedResolve().getElement();
      Assertions.assertTrue(target instanceof PsiMethod);
      Assertions.assertEquals(2, ((PsiMethod)target).getParameterList().getParametersCount());
      Assertions.assertTrue(target.getNavigationElement() instanceof PsiClass);
    });
  }

  @Test
  public void canonicalConstructor() {
    getFixture().addFileToProject("Classes.groovy", "@groovy.transform.Canonical class Foo { int a; int b }");
    GroovyReference ref = referenceByText("new Fo<caret>o(2, 3)");
    ReadAction.run(() -> Assertions.assertTrue(((GrNewExpression)ref.getElement().getParent()).advancedResolve().getElement() instanceof PsiMethod));
  }

  @Test
  public void inheritConstructors() {
    getFixture().addFileToProject("Classes.groovy", "@groovy.transform.InheritConstructors class CustomException extends Exception {}");
    GroovyReference ref = referenceByText("new Cu<caret>stomException(\"msg\")");
    ReadAction.run(() -> Assertions.assertTrue(((GrNewExpression)ref.getElement().getParent()).advancedResolve().getElement() instanceof PsiMethod));
  }

  @Test
  public void partiallyDeclaredType() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Test
  public void generic1() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Test
  public void notAField() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Test
  public void escapedReferenceExpression() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Test
  public void listOfClasses() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);
    });
  }

  @Test
  public void emptyVsMap() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiMethod resolved = ((GrNewExpression)ref.getElement().getParent()).resolveMethod();
      Assertions.assertNotNull(resolved);
      Assertions.assertEquals(0, resolved.getParameterList().getParametersCount());
    });
  }

  @Test
  public void privateScriptMethod() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void aliasedConstructor() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiMethod resolved = ((GrNewExpression)ref.getElement().getParent()).resolveMethod();
      Assertions.assertNotNull(resolved);
      Assertions.assertEquals("JFrame", resolved.getName());
    });
  }

  @Test
  public void fixedVsVarargs1() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiMethod resolved = ((GrNewExpression)ref.getElement().getParent()).resolveMethod();
      Assertions.assertNotNull(resolved);
      final GrParameter[] parameters = ((GrMethod)resolved).getParameters();
      Assertions.assertEquals(1, parameters.length);
      Assertions.assertEquals("int", parameters[0].getType().getCanonicalText());
    });
  }

  @Test
  public void fixedVsVarargs2() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
      final PsiParameter[] parameters = ((PsiMethod)resolved).getParameterList().getParameters();
      Assertions.assertEquals(2, parameters.length);
      Assertions.assertEquals("java.lang.Class", parameters[0].getType().getCanonicalText());
    });
  }

  @Test
  public void reassigned1() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrMethod);
      final GrParameter[] parameters = ((GrMethod)resolved).getParameters();
      Assertions.assertEquals(1, parameters.length);
      Assertions.assertEquals("java.lang.String", parameters[0].getType().getCanonicalText());
    });
  }

  @Test
  public void reassigned2() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrMethod);
      final GrParameter[] parameters = ((GrMethod)resolved).getParameters();
      Assertions.assertEquals(1, parameters.length);
      Assertions.assertEquals("int", parameters[0].getType().getCanonicalText());
    });
  }

  @Test
  public void generics1() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void genericOverriding() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void useOperator() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      final PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrGdkMethod);
    });
  }

  @Test
  public void closureMethodInsideClosure() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      final PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Test
  public void scriptMethodInsideClosure() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      final PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Test
  public void explicitGetter() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      final PsiElement resolved = ref.resolve();
      Assertions.assertNotNull(resolved);
      Assertions.assertFalse(resolved instanceof GrAccessorMethod);
    });
  }

  @Test
  public void groovyAndJavaSamePackage() {
    getFixture().copyFileToProject(getTestName() + "/p/Hu.java", "p/Hu.java");
    PsiReference ref = configureByFile("p/Ha.groovy");
    ReadAction.run(() -> Assertions.assertTrue(ref.resolve() instanceof PsiMethod));
  }

  @Test
  public void unboxBigDecimal() {
    getFixture().addClass("package java.math; public class BigDecimal {}");
    GroovyReference ref = referenceByText("java.lang.Math.<caret>min(0, 0.0)");
    ReadAction.run(() -> {
      Collection<? extends GroovyResolveResult> results = ref.resolve(false);
      Assertions.assertEquals(2, results.size());
    });
  }

  @Test
  public void grvy1157() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Test
  public void grvy1173() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Test
  public void grvy1173_a() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Test
  public void grvy1218() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Test
  public void methodPointer1() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Test
  public void methodPointer2() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof PsiMethod);
    });
  }

  @Test
  public void methodCallTypeFromMultiResolve() {
    runInEdtAndWait(() -> {
      GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").getElement();
      Assertions.assertNull(ref.resolve());
      Assertions.assertTrue(((GrMethodCallExpression)ref.getParent()).getType().equalsToText("java.lang.String"));
    });
  }

  @Test
  public void defaultOverloaded() {
    runInEdtAndWait(() -> {
      GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").getElement();
      Assertions.assertNotNull(ref.resolve());
    });
  }

  @Test
  public void defaultOverloaded2() {
    runInEdtAndWait(() -> {
      GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").getElement();
      Assertions.assertNotNull(ref.resolve());
    });
  }

  @Test
  public void defaultOverloaded3() {
    runInEdtAndWait(() -> {
      GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").getElement();
      Assertions.assertNotNull(ref.resolve());
    });
  }

  @Test
  public void multipleAssignment1() {
    runInEdtAndWait(() -> {
      GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").getElement();
      Assertions.assertNotNull(ref.resolve());
    });
  }

  @Test
  public void multipleAssignment2() {
    runInEdtAndWait(() -> {
      GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").getElement();
      Assertions.assertNotNull(ref.resolve());
    });
  }

  @Test
  public void multipleAssignment3() {
    runInEdtAndWait(() -> {
      GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").getElement();
      Assertions.assertNotNull(ref.resolve());
    });
  }

  @Test
  public void closureIntersect() {
    runInEdtAndWait(() -> {
      GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").getElement();
      Assertions.assertNotNull(ref.resolve());
    });
  }

  @Test
  public void closureCallCurry() {
    runInEdtAndWait(() -> {
      GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").getElement();
      Assertions.assertNotNull(ref.resolve());
    });
  }

  @Test
  public void superFromGString() {
    runInEdtAndWait(() -> {
      GrReferenceExpression ref = (GrReferenceExpression)configureByFile("SuperFromGString.groovy").getElement();
      Assertions.assertNotNull(ref.resolve());
    });
  }

  @Test
  public void nominalTypeIsBetterThanNull() {
    runInEdtAndWait(() -> {
      GrReferenceExpression ref = (GrReferenceExpression)configureByFile("A.groovy").getElement();
      final PsiType type = assertInstanceOf(GrMethod.class, ref.resolve()).getInferredReturnType();
      Assertions.assertNotNull(type);
      Assertions.assertTrue(type.equalsToText(CommonClassNames.JAVA_LANG_STRING));
    });
  }

  @Test
  public void qualifiedSuperMethod() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrMethod);
      Assertions.assertEquals("SuperClass", ((GrMethod)resolved).getContainingClass().getName());
    });
  }

  @Test
  public void qualifiedThisMethod() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      Assertions.assertTrue(resolved instanceof GrMethod);
      Assertions.assertEquals("OuterClass", ((GrMethod)resolved).getContainingClass().getName());
    });
  }

  @Test
  public void printMethodInAnonymousClass1() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> assertInstanceOf(GrGdkMethod.class, ref.resolve()));
  }

  @Test
  public void printMethodInAnonymousClass2() {
    PsiReference ref = configureByFile("B.groovy");
    ReadAction.run(() -> assertInstanceOf(GrGdkMethod.class, ref.resolve()));
  }

  @Test
  public void substituteWhenDisambiguating() {
    getFixture().configureByText("a.groovy", """
      
      class Zoo {
        def Object find(Object x) {}
        def <T> T find(Collection<T> c) {}
      
        {
          fin<caret>d(["a"])
        }
      
      }""");
    runInEdtAndWait(() -> {
      PsiReference ref = getFixture().getFile().findReferenceAt(getFixture().getEditor().getCaretModel().getOffset());
      Assertions.assertEquals(1, ((PsiMethod)ref.resolve()).getTypeParameters().length);
    });
  }

  @Test
  public void fooMethodInAnonymousClass() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      final PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);
      Assertions.assertEquals("A", ((PsiMethod)resolved).getContainingClass().getName());
    });
  }

  @Test
  public void optionalParameters1() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      final PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);
    });
  }

  @Test
  public void optionalParameters2() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      final PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);
    });
  }

  @Test
  public void optionalParameters3() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      final PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);
    });
  }

  @Test
  public void optionalParameters4() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      final PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);
    });
  }

  @Test
  public void notInitializedVariable() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      final PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);
    });
  }

  @Test
  public void methodVsField() {
    final PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      final PsiElement element = ref.resolve();
      assertInstanceOf(PsiMethod.class, element);
    });
  }

  @Test
  public void localVariableVsGetter() {
    final PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      final PsiElement element = ref.resolve();
      assertInstanceOf(GrVariable.class, element);
    });
  }

  @Test
  public void invokeMethodViaThisInStaticContext() {
    final PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      final PsiElement element = ref.resolve();
      Assertions.assertEquals("Class", assertInstanceOf(PsiMethod.class, element).getContainingClass().getName());
    });
  }

  @Test
  public void invokeMethodViaClassInStaticContext() {
    final PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      final PsiElement element = ref.resolve();
      assertInstanceOf(PsiMethod.class, element);
      Assertions.assertEquals("Foo", assertInstanceOf(PsiMethod.class, element).getContainingClass().getName());
    });
  }

  @Test
  public void useInCategory() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);
    });
  }

  @Test
  public void methodVsLocalVariable() {
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrVariable.class, resolved);
    });
  }

  @Test
  public void commandExpressionStatement1() {
    PsiElement method = resolve("A.groovy");
    ReadAction.run(() -> Assertions.assertEquals("foo2", assertInstanceOf(GrMethod.class, method).getName()));
  }

  @Test
  public void commandExpressionStatement2() {
    PsiElement method = resolve("A.groovy");
    ReadAction.run(() -> Assertions.assertEquals("foo3", assertInstanceOf(GrMethod.class, method).getName()));
  }

  @Test
  public void upperCaseFieldAndGetter() {
    runInEdtAndWait(() -> Assertions.assertTrue(resolve("A.groovy") instanceof GrMethod));
  }

  @Test
  public void upperCaseFieldWithoutGetter() {
    runInEdtAndWait(() -> Assertions.assertTrue(resolve("A.groovy") instanceof GrAccessorMethod));
  }

  @Test
  public void spreadOperatorNotList() {
    runInEdtAndWait(() -> assertInstanceOf(GrMethod.class, resolve("A.groovy")));
  }

  @Test
  public void methodChosenCorrect() {
    final PsiElement resolved = resolve("A.groovy");
    ReadAction.run(() -> Assertions.assertEquals("map", assertInstanceOf(GrMethod.class, resolved).getParameterList().getParameters()[0].getName()));
  }

  @Test
  public void resolveCategories() {
    runInEdtAndWait(() -> Assertions.assertNotNull(resolve("A.groovy")));
  }

  @Test
  public void resolveValuesOnEnum() {
    runInEdtAndWait(() -> Assertions.assertNotNull(resolve("A.groovy")));
  }

  @Test
  public void avoidResolveLockInClosure() {
    runInEdtAndWait(() -> Assertions.assertNotNull(resolve("A.groovy")));
  }

  @Test
  public void asType() {
    runInEdtAndWait(() -> assertInstanceOf(GrMethod.class, resolve("A.groovy")));
  }

  @Test
  public void plusAssignment() {
    final PsiElement resolved = resolve("A.groovy");
    ReadAction.run(() -> Assertions.assertEquals("plus", assertInstanceOf(GrMethod.class, resolved).getName()));
  }

  @Test
  public void wrongGdkCallGenerics() {
    getFixture().configureByText("a.groovy", "Map<File,String> map = [:]\n" + "println map.ge<caret>t('', '')");
    runInEdtAndWait(() -> {
      PsiReference ref = getFixture().getFile().findReferenceAt(getFixture().getEditor().getCaretModel().getOffset());
      assertInstanceOf(GrGdkMethod.class, ref.resolve());
    });
  }

  @Test
  public void staticImportInSamePackage() {
    getFixture().addFileToProject("pack/Foo.groovy", """
      package pack
      class Foo {
        static def foo()
      }""");
    PsiReference ref = configureByFile("A.groovy");
    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void stringRefExpr1() {
    runInEdtAndWait(() -> Assertions.assertNotNull(resolve("a.groovy")));
  }

  @Test
  public void stringRefExpr2() {
    runInEdtAndWait(() -> Assertions.assertNotNull(resolve("a.groovy")));
  }

  @Test
  public void stringRefExpr3() {
    runInEdtAndWait(() -> Assertions.assertNotNull(resolve("a.groovy")));
  }

  @Test
  public void nestedWith() {
    runInEdtAndWait(() -> Assertions.assertNotNull(resolve("a.groovy")));
  }

  @Test
  public void category() {
    runInEdtAndWait(() -> Assertions.assertNotNull(resolve("a.groovy")));
  }

  @Test
  public void dontUseQualifierScopeInDGM() {
    runInEdtAndWait(() -> Assertions.assertNull(resolve("a.groovy")));
  }

  @Test
  public void inferPlusType() {
    runInEdtAndWait(() -> Assertions.assertNotNull(resolve("a.groovy")));
  }

  @Test
  public void mixinAndCategory() {
    GroovyReference ref = referenceByText(
      """
        @Category(B)
        class A {
          def foo() {print getName()}
        }
        
        @Mixin(A)
        class B {
          def getName('B');
        }
        
        print new B().f<caret>oo()
        """);

    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      assertInstanceOf(GrGdkMethod.class, resolved);
      assertInstanceOf(GrReflectedMethod.class, ((GrGdkMethod)resolved).getStaticMethod());
    });
  }

  @Test
  public void onlyMixin() {
    GroovyReference ref = referenceByText(
      """
        class A {
          def foo() {print getName()}
        }
        
        @Mixin(A)
        class B {
          def getName('B');
        }
        
        print new B().f<caret>oo()
        """);

    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);
    });
  }

  @Test
  public void twoMixinsInModifierList() {
    GroovyReference ref = referenceByText(
      """
        class PersonHelper {
          def useThePerson() {
            Person person = new Person()
        
            person.getUsername()
            person.get<caret>Name()
          }
        }
        
        @Mixin(PersonMixin)
        @Mixin(OtherPersonMixin)
        class Person { }
        
        class PersonMixin {
          String getUsername() { }
        }
        
        class OtherPersonMixin {
          String getName() { }
        }
        """);

    ReadAction.run(() -> {
      PsiElement resolved = ref.resolve();
      assertInstanceOf(PsiMethod.class, resolved);
    });
  }

  @Test
  public void disjunctionType() {
    GroovyReference ref = referenceByText(
      """
        import java.sql.SQLException
        def test() {
                try {}
                catch (IOException | SQLException ex) {
                    ex.prin<caret>tStackTrace();
                }
        }""");
    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void stringInjectionDontOverrideItParameter() {
    GroovyReference ref = referenceByText(
      """
        [2, 3, 4].collect {"${it.toBigDeci<caret>mal()}"}
        """);
    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void publicVsPrivateConstructor() {
    runInEdtAndWait(() -> {
      PsiMethod resolved =
        ((GrNewExpression)referenceByText("throw new Assertion<caret>Error(\"foo\")").getElement().getParent()).resolveMethod();
      Assertions.assertNotNull(resolved);

      PsiParameter[] parameters = resolved.getParameterList().getParameters();
      Assertions.assertEquals(1, parameters.length);
      GroovyAssertions.assertType("java.lang.String", parameters[0].getType());
    });
  }

  @Test
  public void scriptMethodsInClass() {
    GroovyReference ref = referenceByText(
      """
        class X {
          def foo() {
            scriptMetho<caret>d('1')
          }
        }
        def scriptMethod(String s){}
        """);

    ReadAction.run(() -> Assertions.assertNull(ref.resolve()));
  }

  @Test
  public void staticallyImportedMethodsVsDGMMethods() {
    getFixture().addClass(
      """
        package p;
        public class Matcher{}
        """);
    getFixture().addClass(
      """
        package p;
        class Other {
          public static Matcher is(Matcher m){}
          public static Matcher create(){}
        }""");

    GroovyReference ref = referenceByText(
      """
        import static p.Other.is
        import static p.Other.create
        
        i<caret>s(create())
        
        """);

    ReadAction.run(() -> {
      PsiMethod resolved = assertInstanceOf(PsiMethod.class, ref.resolve());
      Assertions.assertEquals("Other", resolved.getContainingClass().getName());
    });
  }

  @Test
  public void staticallyImportedMethodsVsCurrentClassMethod() {
    getFixture().addClass(
      """
        package p;
        class Other {
          public static Object is(Object m){}
        }""");

    GroovyReference ref = referenceByText(
      """
        import static p.Other.is
        
        class A {
          public boolean is(String o){true}
        
          public foo() {
            print i<caret>s('abc')
          }
        }
        
        """);

    ReadAction.run(() -> {
      PsiMethod resolved = assertInstanceOf(PsiMethod.class, ref.resolve());
      Assertions.assertEquals("A", resolved.getContainingClass().getName());
    });
  }

  @Test
  public void inapplicableStaticallyImportedMethodsVsCurrentClassMethod() {
    getFixture().addClass(
      """
        package p;
        class Other {
          public static Object is(String m){}
        }""");

    GroovyReference ref = referenceByText(
      """
        import static p.Other.is
        
        class A {
          public boolean is(Object o){true}
        
          public foo() {
            print i<caret>s(new Object())
          }
        }
        
        """);

    ReadAction.run(() -> {
      PsiMethod resolved = assertInstanceOf(PsiMethod.class, ref.resolve());
      Assertions.assertEquals("A", resolved.getContainingClass().getName());
    });
  }

  @Test
  public void inferArgumentTypeFromMethod1() {
    GroovyReference ref = referenceByText(
      """
        def bar(String s) {}
        
        def foo(Integer a) {
            bar(a)
        
            a.subst<caret>ring(2)
        }
        """);
    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void inferArgumentTypeFromMethod2() {
    GroovyReference ref = referenceByText(
      """
        def bar(String s) {}
        
        def foo(Integer a) {
          while(true) {
            bar(a)
            a.subst<caret>ring(2)
          }
        }
        """);
    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void inferArgumentTypeFromMethod3() {
    GroovyReference ref = referenceByText(
      """
        def bar(String s) {}
        
        def foo(Integer a) {
            bar(a)
        
            a.int<caret>Value()
        }
        """);
    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void inferArgumentTypeFromMethod4() {
    GroovyReference ref = referenceByText(
      """
        def bar(String s) {}
        
        def foo(Integer a) {
          while(true) {
            bar(a)
            a.intVal<caret>ue()
          }
        }
        """);
    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void staticImportFromSuperClass() {
    GroovyReference ref = referenceByText(
      """
        import static Derived.fo<caret>o
        
        class Base {
            static foo(){print 'foo'}
        }
        
        class Derived extends Base {
        }
        
        foo()
        """);

    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void usageOfStaticImportFromSuperClass() {
    GroovyReference ref = referenceByText(
      """
        import static Derived.foo
        
        class Base {
            static foo(){print 'foo'}
        }
        
        class Derived extends Base {
        }
        
        fo<caret>o()
        """);

    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void mixin() {
    GroovyReference ref = referenceByText(
      """
        @Mixin([Category1, Category2])
        class A {
          def test1(){}
        }
        
        
        @Category(A)
        class Category1 {
          boolean foo() {
            true
        
          }
        }
        
        @Category(A)
        class Category2 {
          void bar() {
            fo<caret>o()
          }
        }
        """);
    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void groovyExtensions() {
    GroovyReference ref = referenceByText(
      """
        package pack
        
        class StringExt {
          static sub(String s) {}
        }
        
        "".su<caret>b()""");

    getFixture().addFileToProject("META-INF/services/org.codehaus.groovy.runtime.ExtensionModule", """
      extensionClasses=pack.StringExt
      """);

    ReadAction.run(() -> Assertions.assertNotNull(ref.resolve()));
  }

  @Test
  public void initializerOfScriptField() {
    GroovyReference ref = referenceByText(
      """
        import groovy.transform.Field
        
        def xx(){5}
        
        @Field
        def aa = 5 + x<caret>x()
        """);
    ReadAction.run(() -> assertInstanceOf(GrMethod.class, ref.resolve()));
  }

  @Test
  public void runtimeMixin1() {
    resolveTest(
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        metaClass.mixin(Foo)
        d<caret>oSmth()
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin2() {
    resolveTest(
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        this.metaClass.mixin(Foo)
        do<caret>Smth()
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin3() {
    getFixture().configureByText("_.groovy",
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        _.metaClass.mixin(Foo)
        do<caret>Smth()
        """);
    resolveTest(PsiMethod.class);
  }

  @Test
  public void runtimeMixin4() {
    getFixture().configureByText("_.groovy",
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        _.class.mixin(Foo)
        do<caret>Smth()
        """);
    resolveTest(PsiMethod.class);
  }

  @Test
  public void runtimeMixin5() {
    getFixture().configureByText("_.groovy",
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        _.mixin(Foo)
        do<caret>Smth()
        """);
    resolveTest(PsiMethod.class);
  }

  @Test
  public void runtimeMixin6() {
    resolveTest(
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        class _a {
          def foo() {
            metaClass.mixin(Foo)
            d<caret>oSmth()
          }
        }
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin7() {
    resolveTest(
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        class _a {
          def foo() {
            this.metaClass.mixin(Foo)
            do<caret>Smth()
          }
        }
        
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin8() {
    resolveTest(
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        class _a {
          def foo() {
            _a.metaClass.mixin(Foo)
            do<caret>Smth()
          }
        }
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin9() {
    resolveTest(
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        class _a {
          def foo() {
            _a.class.mixin(Foo)
            do<caret>Smth()
          }
        }
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin10() {
    resolveTest(
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        class _a {
          def foo() {
            _a.mixin(Foo)
            do<caret>Smth()
          }
        }
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin11() {
    resolveTest(
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        metaClass.mixin(Foo)
        new _a().d<caret>oSmth()
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin12() {
    resolveTest(
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        this.metaClass.mixin(Foo)
        new _a().do<caret>Smth()
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin13() {
    getFixture().configureByText("_.groovy",
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        _.metaClass.mixin(Foo)
        new _a().do<caret>Smth()
        """);
    resolveTest(PsiMethod.class);
  }

  @Test
  public void runtimeMixin14() {
    getFixture().configureByText("_.groovy",
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        _.class.mixin(Foo)
        new _a().do<caret>Smth()
        """);
    resolveTest(PsiMethod.class);
  }

  @Test
  public void runtimeMixin15() {
    getFixture().configureByText("_.groovy",
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        _.mixin(Foo)
        new _a().do<caret>Smth()
        """);
    resolveTest(PsiMethod.class);
  }

  @Test
  public void runtimeMixin16() {
    resolveTest(
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        class _a {
          def foo() {
            metaClass.mixin(Foo)
            new _a().d<caret>oSmth()
          }
        }
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin17() {
    resolveTest(
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        class _a {
          def foo() {
            this.metaClass.mixin(Foo)
            new _a().do<caret>Smth()
          }
        }
        
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin18() {
    resolveTest(
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        class _a {
          def foo() {
            _a.metaClass.mixin(Foo)
            new _a().do<caret>Smth()
          }
        }
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin19() {
    resolveTest(
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        class _a {
          def foo() {
            _a.class.mixin(Foo)
            new _a().do<caret>Smth()
          }
        }
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin20() {
    resolveTest(
      """
        class Foo {
            public static void doSmth(Script u) {
                println "hello"
            }
        }
        
        class _a {
          def foo() {
            _a.mixin(Foo)
            new _a().do<caret>Smth()
          }
        }
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin21() {
    resolveTest(
      """
        class Foo {
            public void doSmth() {
                println "hello"
            }
        }
        
        class _a {
          def foo() {
            _a.mixin(Foo)
            new _a().do<caret>Smth()
          }
        }
        """, PsiMethod.class);
  }

  @Test
  public void runtimeMixin22() {
    resolveTest(
      """
        class ReentrantLock {}
        
        ReentrantLock.metaClass.withLock = { nestedCode -> }
        
        new ReentrantLock().withLock {
            fo<caret>o(3)
        }
        """, null);
  }

  @Test
  public void runtimeMixin23() {
    runInEdtAndWait(() -> {
      Assertions.assertNotNull(
        resolveTest("""
                      class ReentrantLock {}
                    
                      ReentrantLock.metaClass.withLock = { nestedCode -> }
                    
                      new ReentrantLock().withLock {
                          withL<caret>ock(2)
                      }
                      """, PsiElement.class));
    });
  }

  @Test
  public void runnableVsCallable() {
    final PsiMethod method = resolveTest(
      """
        import java.util.concurrent.Callable
        
        void bar(Runnable c) {}
        
        void bar(Callable<?> c) {}
        
        b<caret>ar {
            print 2
        }
        
        """, PsiMethod.class);

    ReadAction.run(() -> Assertions.assertTrue(method.getParameterList().getParameters()[0].getType().equalsToText("java.lang.Runnable")));
  }

  @Test
  public void oneArgVsEllipsis1() {
    PsiMethod method = resolveTest(
      """
        class X {
            void foo(Object... args) {
                print 'many'
            }
        
            void foo(Object arg) {
                print 'one'
            }
        
            void foo() {
                print 'none'
            }
        }
        
        new X().fo<caret>o('abc')""", PsiMethod.class);

    ReadAction.run(() -> Assertions.assertFalse(method.getParameterList().getParameters()[0].getType() instanceof PsiEllipsisType));
  }

  @Test
  public void oneArgVsEllipsis2() {
    PsiMethod method = resolveTest(
      """
        class X {
            void foo(Object arg) {
                print 'one'
            }
        
            void foo(Object... args) {
                print 'many'
            }
        
            void foo() {
                print 'none'
            }
        }
        
        new X().fo<caret>o('abc')""", PsiMethod.class);

    ReadAction.run(() -> Assertions.assertFalse(method.getParameterList().getParameters()[0].getType() instanceof PsiEllipsisType));
  }

  @Test
  public void methodWithLiteralName() {
    resolveTest("""
                  def 'a\\'bc'(){}
                  "a'b<caret>c"()
                  """, GrMethod.class);
  }

  @Test
  public void valueOf() {
    final PsiMethod valueof = resolveTest(
      """
        enum MyEnum {
            FOO, BAR
        }
        
        
        MyEnum myEnum
        myEnum = MyEnum.va<caret>lueOf('FOO')
        """, PsiMethod.class);

    ReadAction.run(() -> Assertions.assertEquals(1, valueof.getParameterList().getParametersCount()));
  }

  @Disabled("groovy actually doesn't care about return type, TODO check this")
  @Test
  public void resolveOverloadedReturnType() {
    getFixture().addClass("class PsiModifierList {}");
    getFixture().addClass("class GrModifierList extends PsiModifierList {}");
    getFixture().addClass("class GrMember {" + "  GrModifierList get();" + "}");
    getFixture().addClass("class PsiClass {" + "  PsiModifierList get();" + "}");

    getFixture().addClass("class GrTypeDefinition extends PsiClass, GrMember {}");

    final PsiMethod method = resolveTest("new GrTypeDefinition().ge<caret>t()", PsiMethod.class);

    ReadAction.run(() -> Assertions.assertEquals("GrModifierList", method.getReturnType().getCanonicalText()));
  }

  @Test
  public void contradictingPropertyAccessor() {
    PsiMethod method = resolveTest(
      """
        class A {
            def setFoo(Object o) {
                print 'method'
            }
        
            int foo = 5
        }
        
        new A().setF<caret>oo(2)
        """, PsiMethod.class);

    ReadAction.run(() -> assertInstanceOf(GrMethodImpl.class, method));
  }

  @Test
  public void contradictingPropertyAccessor2() {
    PsiMethod method = resolveTest(
      """
        class A {
            def setFoo(Object o) {
                print 'method'
            }
        
            int foo = 5
        }
        
        new A().f<caret>oo = 2
        """, PsiMethod.class);

    ReadAction.run(() -> assertInstanceOf(GrMethodImpl.class, method));
  }

  @Test
  public void resoleAnonymousMethod() {
    resolveTest(
      """
        def anon = new Object() {
          def foo() {
            print 2
          }
        }
        
        anon.fo<caret>o()
        """, GrMethod.class);
  }

  @Test
  public void mapAccess() {
    resolveTest(
      """
        Map<String, List<String>> foo() {}
        
        foo().bar.first().subs<caret>tring(1, 2)
        """, PsiMethod.class);
  }

  @Test
  public void mixinClosure() {
    resolveTest(
      """
        def foo() {
            def x = { a -> print a}
            Integer.metaClass.abc = { print 'something' }
            1.a<caret>bc()
        }
        """, PsiMethod.class);
  }

  @Test
  public void preferCategoryMethods() {
    GrVariable resolved = resolveTest(
      """
        class TimeCategory {
            public static TimeDuration minus(final Date lhs, final Date rhs) {
                return new TimeDuration()
            }
        }
        
        class TimeDuration {}
        
        void bug() {
            use (TimeCategory) {
                def duration = new Date() - new Date()
                print durat<caret>ion
            }
        }
        """, GrVariable.class);

    ReadAction.run(() -> Assertions.assertEquals("TimeDuration", resolved.getTypeGroovy().getCanonicalText()));
  }

  @Test
  public void preferCategoryMethods2() {
    GrVariable resolved = resolveTest(
      """
        class TimeCategory {
            public static TimeDuration minus(final Date lhs, final Date rhs) {
                return new TimeDuration((int) days, hours, minutes, seconds, (int) milliseconds);
            }
        }
        
        class TimeDuration {}
        
        void bug() {
            use (TimeCategory) {
                def duration = new Date().minus(new Date())
                print durat<caret>ion
            }
        }
        """, GrVariable.class);

    ReadAction.run(() -> Assertions.assertEquals("TimeDuration", resolved.getTypeGroovy().getCanonicalText()));
  }

  @Test
  public void negatedIf() {
    resolveTest("""
                  def foo(x) {
                    if (!(x instanceof String)) return
                  
                    x.subst<caret>ring(1)
                  }
                  """, PsiMethod.class);
  }

  @Test
  public void inferredTypeInsideGStringInjection() {
    resolveTest(
      """
        class A {}
        class B extends A {
            String bar() {'bar'}
        }
        
        def foo(A b) {
            if (b instanceof B) {
                doSomethingElse("Message: ${b.ba<caret>r()}")
        
            }
        }
        """, PsiMethod.class);
  }

  @Test
  public void IDEA_110562() {
    assertNotResolved(
      """
        interface GrTypeDefinition {
            def baz()
        }
        
        class Foo {
            private static void extractSuperInterfaces(Object subclass) {
                if (!(subclass instanceof GrTypeDefinition)) {
                    foo()
                    subclass.b<caret>az()
                }
            }
        
            static def foo() {}
        }
        """);
  }

  private void assertNotResolved(String text) {
    final GroovyReference ref = referenceByText(text);
    ReadAction.run(() -> {
      Assertions.assertNotNull(ref);
      final PsiElement resolved = ref.resolve();
      Assertions.assertNull(resolved);
    });
  }

  @Test
  public void IDEA_110562_2() {
    resolveTest(
      """
        interface GrTypeDefinition {
            def baz()
        }
        
        class Foo {
            private static void extractSuperInterfaces(Object subclass) {
                if (subclass instanceof GrTypeDefinition) {
                    foo()
                    subclass.b<caret>az()
                }
            }
        
            static def foo() {}
        }
        """, PsiMethod.class);
  }

  @Test
  public void instanceOf1() {
    resolveTest(
      """
        class Foo {
          def foo(){}
        }
        
        class Bar {
          def bar()
        }
        
        def bar(Object o) {
          if (o instanceof Foo && o.fo<caret>o() && o.bar()) {
            print o.foo()
          }
        }
        """, PsiMethod.class);
  }

  @Test
  public void instanceOf2() {
    assertNotResolved(
      """
        class Foo {
          def foo(){}
        }
        
        class Bar {
          def bar()
        }
        
        def bar(Object o) {
          if (o instanceof Foo && o.foo() && o.ba<caret>r()) {
            print o.foo()
          }
        }
        """);
  }

  @Test
  public void instanceOf3() {
    resolveTest(
      """
        class Foo {
          def foo(){}
        }
        
        class Bar {
          def bar()
        }
        
        def bar(Object o) {
          if (o instanceof Foo && o instanceof Bar && o.fo<caret>o() && o.bar()) {
            print o.foo()
          }
        }
        """, PsiMethod.class);
  }

  @Test
  public void instanceOf4() {
    resolveTest(
      """
        class Foo {
          def foo(){}
        }
        
        class Bar {
          def bar()
        }
        
        def bar(Object o) {
          if (o instanceof Foo && o instanceof Bar && o.foo() && o.b<caret>ar()) {
            print o.foo()
          }
        }
        """, PsiMethod.class);
  }

  @Test
  public void instanceOf5() {
    assertNotResolved(
      """
        class Foo {
          def foo(){}
        }
        
        class Bar {
          def bar()
        }
        
        def bar(Object o) {
          if (o instanceof Foo && o.foo() && o.bar()) {
            print o.foo()
          }
          else {
            print o.fo<caret>o()
          }
        }
        """);
  }

  @Test
  public void instanceOf6() {
    assertNotResolved(
      """
        class Foo {
          def foo(){}
        }
        
        class Bar {
          def bar()
        }
        
        def bar(Object o) {
          if (o instanceof Foo && o instanceof Bar && o.foo() && o.bar()) {
            print o.foo()
          }
          else {
            print o.fo<caret>o()
          }
        }
        """);
  }

  @Test
  public void instanceOf7() {
    assertNotResolved(
      """
        class Foo {
          def foo(){}
        }
        
        class Bar {
          def bar()
        }
        
        def bar(Object o) {
          if (o instanceof Foo && o instanceof Bar && o.foo() && o.bar()) {
            print o.foo()
          }
          else {
            print o.ba<caret>r()
          }
        }
        """);
  }

  @Test
  public void binaryWithQualifiedRefsInArgs() {
    GrOperatorReference ref = referenceByText(
      """
        class Base {
            def or(String s) {}
            def or(Base b) {}
        
            public static Base SHOW_NAME = new Base()
            public static Base SHOW_TYPE = new Base()
        }
        
        class GrTypeDefinition  {
            def foo() {
                print (Base.SHOW_NAME <caret>| Base.SHOW_TYPE)
        
            }
        }
        """, GrOperatorReference.class);

    ReadAction.run(() -> {
      Assertions.assertEquals(1, ref.multiResolve(false).length);
      Assertions.assertTrue(ref.multiResolve(true).length > 1);
    });
  }

  @Test
  public void staticMethodInInstanceContext() {
    GrMethod resolved = resolveTest(
      """
        class Foo {
            def foo(String s){}
            static def foo(File f){}
        }
        
        new Foo().f<caret>oo(new File(''))
        """, GrMethod.class);

    ReadAction.run(() -> Assertions.assertTrue(resolved.hasModifierProperty(PsiModifier.STATIC)));
  }

  @Test
  public void baseScript() {
    getFixture().addClass("class CustomScript extends Script { void foo() {} }");
    resolveTest(
      """
        import groovy.transform.BaseScript
        
        @BaseScript
        CustomScript myScript;
        
        f<caret>oo()
        """, PsiMethod.class);
  }

  @Disabled("requires overhaul in static import resolution")
  @Test
  public void importStaticVSDGM() {
    PsiMethod method = resolveTest(
      """
        import static Bar.is
        
        class Foo {
            void foo() {
                i<caret>s(null)
            }
        }
        
        class Bar {
            static boolean is(Class c) {
                println 'bar'
                return true
            }
        }
        """, PsiMethod.class);

    ReadAction.run(() -> {
      PsiClass clazz = method.getContainingClass();
      Assertions.assertNotNull(clazz);
      Assertions.assertEquals("Bar", clazz.getQualifiedName());
    });
  }

  @Test
  public void importStaticPrint() {
    PsiMethod print = resolveTest(
      """
        import static C.print
        
        new Runnable() {
            void run() {
                pri<caret>nt "wow";
            }
        }.run()
        
        class C {
            static def print(String s) {print 'hjk'}
        }
        """, PsiMethod.class);

    ReadAction.run(() -> {
      PsiClass clazz = print.getContainingClass();
      Assertions.assertNotNull(clazz);
      Assertions.assertEquals("C", clazz.getQualifiedName());
    });
  }

  @Test
  public void printInClosure() {
    PsiMethod print = resolveTest(
      """
        class C {
            static def print(String s) {prin<caret>t 'hjk'}
        }
        """, PsiMethod.class);

    ReadAction.run(() -> {
      PsiClass clazz = print.getContainingClass();
      Assertions.assertNotNull(clazz);
      Assertions.assertEquals("C", clazz.getQualifiedName());
    });
  }

  @Test
  public void print() {
    PsiMethod print = resolveTest(
      """
        import static C.print
        
        def cl = {pr<caret>int 'abc'}
        
        class C {
            static def print(String s) {print 'hjk'}
        }
        """, PsiMethod.class);

    ReadAction.run(() -> {
      PsiClass clazz = print.getContainingClass();
      Assertions.assertNotNull(clazz);
      Assertions.assertEquals("C", clazz.getQualifiedName());
    });
  }

  @Test
  public void scriptMethodVSStaticImportInsideAnonymous() {
    PsiMethod method = resolveTest(
      """
        import static C.abc
        
        class C {
            static def abc(c) {
                print 2
            }
        }
        new Runnable() {
            @Override
            void run() {
                ab<caret>c '2'
            }
        }.run()
        
        def abc(String s) { print 'hjk' }
        """, PsiMethod.class);
    ReadAction.run(() -> {
      PsiClass clazz = method.getContainingClass();
      Assertions.assertNotNull(clazz);
      Assertions.assertEquals("C", clazz.getQualifiedName());
    });
  }

  @Test
  public void scriptMethodVSStaticImportInsideClosure() {
    PsiMethod method = resolveTest(
      """
        import static C.abc
        
        class C {
            static def abc(c) {
                print 2
            }
        }
        def cl = {
            ab<caret>c '2'
        }
        
        def abc(String s) { print 'hjk' }
        """, PsiMethod.class);
    ReadAction.run(() -> {
      PsiClass clazz = method.getContainingClass();
      Assertions.assertNotNull(clazz);
      Assertions.assertEquals("C", clazz.getQualifiedName());
    });
  }

  @Test
  public void scriptMethodVSStaticImportInsideLambda() {
    PsiMethod method = resolveTest(
      """
        import static C.abc
        
        class C {
            static def abc(c) {
                print 2
            }
        }
        def cl = () -> {
            ab<caret>c '2'
        }
        
        def abc(String s) { print 'hjk' }
        """, PsiMethod.class);
    ReadAction.run(() -> {
      PsiClass clazz = method.getContainingClass();
      Assertions.assertNotNull(clazz);
      Assertions.assertEquals("C", clazz.getQualifiedName());
    });
  }

  @Test
  public void scriptMethodVSStaticImportInsideScript() {
    PsiMethod method = resolveTest(
      """
        import static C.abc
        
        class C {
            static def abc(c) {
                print 2
            }
        }
        
        ab<caret>c '2'
        
        def abc(String s) { print 'hjk' }
        """, PsiMethod.class);
    ReadAction.run(() -> {
      PsiClass clazz = method.getContainingClass();
      Assertions.assertNotNull(clazz);
      assertInstanceOf(GroovyScriptClass.class, clazz);
    });
  }

  @Test
  public void localStringVSDefault() {
    PsiClass clazz = resolveTest(
      """
        class String {}
        
        new Str<caret>ing()
        """, PsiClass.class);

    ReadAction.run(() -> Assertions.assertEquals("String", clazz.getQualifiedName()));
  }

  @Test
  public void localVarVSStaticImport() {
    resolveTest(
      """
        import static Abc.foo
        
        class Abc {
            static def foo() { print 'static' }
        }
        
        def foo =  { print 'closure' }
        
        
        fo<caret>o()
        """, GrVariable.class);
  }

  @Test
  public void instanceMethodVSStaticImport() {
    PsiMethod method = resolveTest(
      """
        import static C.abc
        
        class C {
            static def abc(a) {}
        }
        
        class B {
            def abc(a) {}
        
            void bar() {
               a<caret>bc(x)
            }
        }
        """, PsiMethod.class);
    ReadAction.run(() -> {
      PsiClass clazz = method.getContainingClass();
      Assertions.assertNotNull(clazz);
      Assertions.assertEquals("B", clazz.getQualifiedName());
    });
  }

  @Test
  public void useVSStaticImport() {
    PsiMethod method = resolveTest(
      """
        import static C.abc
        
        class C {
            static def abc(c) {
                print 'static '
            }
        }
        
        class D {
            static def abc(c, d) {
                print 'mixin'
            }
        }
        
        class Fo {
            def bar() {
                use(D) {
                    ab<caret>c(2)
                }
            }
        }
        """, PsiMethod.class);
    ReadAction.run(() -> {
      PsiClass clazz = method.getContainingClass();
      Assertions.assertNotNull(clazz);
      Assertions.assertEquals("C", clazz.getQualifiedName());
    });
  }

  @Test
  public void superReferenceWithTraitQualifier() {
    PsiMethod method = resolveTest(
      """
        trait A {
            String exec() { 'A' }
        }
        trait B {
            String exec() { 'B' }
        }
        
        class C implements A,B {
            String exec() {A.super.exe<caret>c() }
        }
        """, PsiMethod.class);
    ReadAction.run(() -> Assertions.assertEquals("A", method.getContainingClass().getName()));
  }

  @Test
  public void superReferenceWithTraitQualifier2() {
    PsiMethod method = resolveTest(
      """
        trait A {
            String exec() { 'A' }
        }
        trait B {
            String exec() { 'B' }
        }
        
        class C implements A, B {
            String exec() {B.super.exe<caret>c() }
        }
        """, PsiMethod.class);
    ReadAction.run(() -> Assertions.assertEquals("B", method.getContainingClass().getName()));
  }

  @Test
  public void clashingTraitMethods() {
    GrTraitMethod method = resolveTest(
      """
        trait A {
            String exec() { 'A' }
        }
        trait B {
            String exec() { 'B' }
        }
        
        class C implements A, B {
            String foo() {exe<caret>c() }
        }
        """, GrTraitMethod.class);
    ReadAction.run(() -> Assertions.assertEquals("B", method.getPrototype().getContainingClass().getName()));
  }

  @Test
  public void traitMethodFromAsOperator1() {
    resolveTest(
      """
        trait A {
          def foo(){}
        }
        class B {
          def bar() {}
        }
        
        def v = new B() as A
        v.fo<caret>o()
        """, PsiMethod.class);
  }

  @Test
  public void traitMethodFromAsOperator2() {
    resolveTest(
      """
        trait A {
          def foo(){}
        }
        class B {
          def bar() {}
        }
        
        def v = new B() as A
        v.ba<caret>r()
        """, PsiMethod.class);
  }

  @Test
  public void methodReferenceWithDefaultParameters() {
    GroovyReference ref = referenceByText(
      """
        class X {
          def foo(def it = null) {print it}
        
          def bar() {
            print this.&f<caret>oo
          }
        }
        """);
    ReadAction.run(() -> {
      GroovyResolveResult[] results = ref.multiResolve(false);
      Assertions.assertEquals(2, results.length);
      for (GroovyResolveResult result : results) {
        Assertions.assertTrue(result.getElement() instanceof GrReflectedMethod);
        Assertions.assertTrue(result.isValidResult());
      }
    });
  }

  @Test
  public void staticTraitMethodGenericReturnType() {
    GrTraitMethod method = resolveTest(
      """
        trait GenericSourceTrait<E> {
            static E someOtherStaticMethod() {null}
        }
        class SourceConcrete implements GenericSourceTrait<String> {}
        SourceConcrete.someOtherStatic<caret>Method()
        """, GrTraitMethod.class);
    ReadAction.run(() -> Assertions.assertEquals("java.lang.String", method.getReturnType().getCanonicalText()));
  }

  @Test
  public void resolveMethodWithClassQualifier() {
    getFixture().addClass(
      """
        package foo.bar;
        
        public class A {
          public static void foo() {}
          public static String getCanonicalName() {return "";}
        }
        """);
    LinkedHashMap<String, String> map = new LinkedHashMap<>(6);
    runInEdtAndWait(() -> {
      map.put("A.fo<caret>o()", "foo.bar.A");
      map.put("A.class.fo<caret>o()", "foo.bar.A");
      map.put("A.simpleN<caret>ame", "java.lang.Class");
      map.put("A.class.simpleN<caret>ame", "java.lang.Class");
      map.put("A.canonicalN<caret>ame", "foo.bar.A");
      map.put("A.class.canonicalN<caret>ame", "foo.bar.A");
      for (Map.Entry<String, String> entry : map.entrySet()) {
        String expression = entry.getKey();
        String expectedClass = entry.getValue();
        GroovyReference ref = referenceByText("import foo.bar.A; " + expression);
        PsiElement element = ref.resolve();
        Assertions.assertTrue(element instanceof PsiMember);
        Assertions.assertEquals(((PsiMember)element).getContainingClass().getQualifiedName(), expectedClass);
      }
    });
  }

  @Test
  public void lowPriorityForVarargsMethod() {
    GrMethod method = resolveTest("""
                                    def foo(Object... values) {}
                                    def foo(Object[] values, Closure c) {}
                                    
                                    fo<caret>o(new Object[0], {})
                                    """, GrMethod.class);
    ReadAction.run(() -> {
      Assertions.assertFalse(method.isVarArgs());
      Assertions.assertEquals(2, method.getParameters().length);
    });
  }

  @Test
  public void compareToWithIntegerAndBigDecimal() {
    getFixture().addClass("package java.math; public class BigDecimal extends Number implements Comparable<BigDecimal> {}");
    resolveTest("""
                  BigDecimal b = 1
                  1.comp<caret>areTo(b)
                  1 > b
                  """, GrGdkMethod.class);
    getFixture().enableInspections(GroovyAssignabilityCheckInspection.class);
    getFixture().checkHighlighting();
  }

  @Test
  public void resolveAutoImplement() {
    GrLightMethodBuilder method = resolveTest(
      """
        import groovy.transform.AutoImplement
        
        @AutoImplement
        class SomeClass extends List<Integer> {
        }
        
        new SomeClass().si<caret>ze()
        """, GrLightMethodBuilder.class);
    ReadAction.run(() -> Assertions.assertTrue(method.getOriginInfo().contains("@AutoImplement")));
  }

  @Test
  public void resolveAutoImplementImplemented() {
    resolveTest(
      """
        import groovy.transform.AutoImplement
        
        @AutoImplement
        class SomeClass extends List<Integer> {
          @Override
          public int size() {return 0}
        }
        
        new SomeClass().si<caret>ze()
        """, GrMethodImpl.class);
  }

  @Test
  public void preferVarargsInNoArgCall() {
    GroovyFile file = (GroovyFile)getFixture().configureByText("_.groovy", """
      class A {
        A(String... a) { println "varargs" }
        A(A a) { println "single" }
      }
      
      new A()
      """);

    ReadAction.run(() -> {
      GrNewExpression expression = (GrNewExpression)file.getStatements()[file.getStatements().length - 1];
      PsiMethod resolved = expression.resolveMethod();
      Assertions.assertTrue(resolved instanceof GrMethod);
      Assertions.assertTrue(resolved.isVarArgs());
    });
  }

  @Test
  public void staticMethodViaClassInstance() {
    resolveTest(
      """
        class A { public static foo() { 45 } }
        def a = A // class instance
        a.<caret>foo()
        """, GrMethod.class);
  }

  @Test
  public void arrayVsSingleWithSimpleArgument() {
    GrMethod method = resolveTest(
      """
        static void foo(Object t) {}
        static void foo(Object[] values) {}
        static void usage(String label) { <caret>foo(label) }
        """, GrMethod.class);
    ReadAction.run(() -> Assertions.assertTrue(method.getParameterList().getParameters()[0].getType().equalsToText("java.lang.Object")));
  }

  @Test
  public void arrayVsSingleWithArrayArgument() {
    GrMethod method = resolveTest(
      """
        static void foo(Object t) {}
        static void foo(Object[] values) {}
        static void usage(String[] label) { <caret>foo(label) }
        """, GrMethod.class);
    ReadAction.run(() -> Assertions.assertTrue(method.getParameterList().getParameters()[0].getType().equalsToText("java.lang.Object[]")));
  }

  @Test
  public void arrayVsSingleWithNullArgument() {
    GrMethod method = resolveTest(
      """
        static void foo(Object t) {}
        static void foo(Object[] values) {}
        <caret>foo(null)
        """, GrMethod.class);
    ReadAction.run(() -> Assertions.assertTrue(method.getParameterList().getParameters()[0].getType().equalsToText("java.lang.Object")));
  }

  @Test
  public void varargVsSingleWithArrayArgument() {
    GrMethod method = resolveTest("""
                                    static void foo(Object t) {}
                                    static void foo(Object... values) {}
                                    static usage(String[] label) { <caret>foo(label) }
                                    """, GrMethod.class);
    ReadAction.run(() -> Assertions.assertTrue(method.getParameterList().getParameters()[0].getType().equalsToText("java.lang.Object...")));
  }

  @Test
  public void varargVsSingleWithSimpleArgument() {
    GrMethod method = resolveTest("""
                                    static void foo(Object t) {}
                                    static void foo(Object... values) {}
                                    static void usage(String label) { <caret>foo(label) }
                                    """, GrMethod.class);
    ReadAction.run(() -> Assertions.assertTrue(method.getParameterList().getParameters()[0].getType().equalsToText("java.lang.Object")));
  }

  @Test
  public void varargVsSingleWithNullArgument() {
    GrMethod method = resolveTest("""
                                    static void foo(Object t) {}
                                    static void foo(Object... values) {}
                                    <caret>foo(null)
                                    """, GrMethod.class);
    ReadAction.run(() -> Assertions.assertTrue(method.getParameterList().getParameters()[0].getType().equalsToText("java.lang.Object")));
  }

  @Test
  public void varargVsPositional2() {
    GrMethod method = resolveTest(
      """
        static def foo(Object o) { "obj $o" }
        static def foo(Object[] oo) { "arr $oo" }
        static usage(Object a) { <caret>foo(a) }
        """, GrMethod.class);
    ReadAction.run(() -> Assertions.assertTrue(method.getParameterList().getParameters()[0].getType().equalsToText(CommonClassNames.JAVA_LANG_OBJECT)));
  }

  @Test
  public void listVsObjectArrayParamWithNullArgument() {
    GrMethod method = resolveTest(
      """
        def foo(List l) {}
        def foo(Object[] o) {}
        <caret>foo(null)
        """, GrMethod.class);
    ReadAction.run(() -> Assertions.assertTrue(method.getParameterList().getParameters()[0].getType().equalsToText("java.util.List")));
  }

  @Test
  public void IDEA_217978() {
    GrGdkMethod method = resolveTest(
      """
        def <T extends List<Integer>> void foo(T a) {
          a.eve<caret>ry {true}
        }
        """, GrGdkMethod.class);
    ReadAction.run(() -> Assertions.assertEquals("every", method.getStaticMethod().getName()));
  }

  @Test
  public void IDEA_216095() {
    GrMethod method = resolveTest(
      """
        void foo(Integer i, Class c, Object... objects){
        }
        
        void foo(Object i, Class... classes){
        }
        
        fo<caret>o(1, Object, Object)
        """, GrMethod.class);
    ReadAction.run(() -> Assertions.assertEquals(3, method.getParameters().length));
  }

  @Test
  public void IDEA_216095_2() {
    GrMethod method = resolveTest(
      """
        void foo(Integer i, Object... objects){
        }
        
        void foo(Object i){
        }
        
        fo<caret>o(1)
        """, GrMethod.class);
    ReadAction.run(() -> Assertions.assertEquals(1, method.getParameters().length));
  }

  @Test
  public void IDEA_216095_3() {
    GrMethod method = resolveTest("""
                                    void foo(Double i, Object... objects){
                                    }
                                    
                                    void foo(Object i, Integer... objects){
                                    }
                                    
                                    f<caret>oo(1, 1)
                                    """, GrMethod.class);
    ReadAction.run(() -> Assertions.assertEquals("java.lang.Integer...", method.getParameters()[1].getType().getCanonicalText()));
  }

  @Test
  public void IDEA_216095_4() {
    GrMethod method = resolveTest("""
                                    void foo(Double i, Object... objects){
                                    }
                                    
                                    void foo(Object i, Integer... objects){
                                    }
                                    
                                    f<caret>oo(1, 1, new Object())
                                    """, GrMethod.class);
    ReadAction.run(() -> Assertions.assertEquals("java.lang.Object...", method.getParameters()[1].getType().getCanonicalText()));
  }

  @Test
  public void IDEA_216095_5() {
    GrMethod method = resolveTest("""
                                    void foo(Runnable... objects){
                                    }
                                    
                                    void foo(Object... objects){
                                    }
                                    
                                    f<caret>oo(null)
                                    """, GrMethod.class);
    ReadAction.run(() -> Assertions.assertEquals("java.lang.Object...", method.getParameters()[0].getType().getCanonicalText()));
  }

  @Test
  public void resolveCallsInsideClosure() {
    resolveTest(
      """
        def f() {
          def x = 'q'
          1.with {
            print(x.is<caret>Empty())
          }
        }""", PsiMethod.class);
  }

  @Test
  public void resolveCallsInsideClosureWithCompileStatic() {
    resolveTest(
      """
        import groovy.transform.CompileStatic
        
        @CompileStatic
        def test() {
            1.with { r -> def x = 1; r.ti<caret>mes { x.byteValue() } }
        }""", PsiMethod.class);
  }

  @Test
  public void resolveCallsInsideNestedClosure() {
    resolveTest(
      """
        def test() {
            1.with { def x = 1; it.times { x.byt<caret>eValue() } }
        }""", PsiMethod.class);
  }
}
