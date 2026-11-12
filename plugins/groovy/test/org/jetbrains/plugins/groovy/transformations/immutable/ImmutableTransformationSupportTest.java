// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.transformations.immutable;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.codeInspection.assignment.GroovyAssignabilityCheckInspection;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members.GrMethodImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.jetbrains.plugins.groovy.LightGroovyFixtures.lightGroovyFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ImmutableTransformationSupportTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_LATEST);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightGroovyFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("classes.groovy", """
      @groovy.transform.Immutable(copyWith = true)
      class CopyWith {
        String stringProp
        Integer integerProp
      }
      """);
    myFixture.enableInspections(GrUnresolvedAccessInspection.class, GroovyAssignabilityCheckInspection.class);
  }

  @Test
  public void testCopyWithNoProperties() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
      @groovy.transform.Immutable(copyWith = true)
      class CopyWithNoProps {}
      new CopyWithNoProps().<warning descr="Cannot resolve symbol 'copyWith'">copyWith</warning>()
      """);
    myFixture.checkHighlighting();
  }

  @Test
  public void testCopyWithExistsWithSingleParameter() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addFileToProject("_.groovy", """
      @groovy.transform.Immutable(copyWith = true)
      class CopyWithExistingMethod {
        def copyWith(a) {}
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiClass clazz = myFixture.findClass("CopyWithExistingMethod");
      PsiMethod[] methods = clazz.findMethodsByName("copyWith", false);
      assertEquals(1, methods.length);
      assertTrue(methods[0] instanceof GrMethodImpl);
    });
  }

  @Test
  public void testCopyWithResolveArguments() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
      def usage(CopyWith cw) {
        cw.copyWith(st<caret>ringProp: 'hello')
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiReference ref = myFixture.getFile().findReferenceAt(myFixture.getEditor().getCaretModel().getOffset());
      assertNotNull(ref);
      PsiElement resolved = ref.resolve();
      assertTrue(resolved instanceof GrField);
    });
  }

  @Test
  public void testCopyWithCheckArgumentTypes() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
      def usage(CopyWith cw) {
        cw.copyWith(
          stringProp: 42,\s
          integerProp: <warning descr="Type of argument 'integerProp' can not be 'String'">'23'</warning>,\s
          unknownProp: new Object()
        )\s
      }
      """);
    myFixture.checkHighlighting();
  }

  @Test
  public void testCopyWithCompleteProperties() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("_.groovy", """
      def usage(CopyWith cw) {
        cw.copyWith(<caret>)
      }
      """);
    myFixture.complete(CompletionType.BASIC);
    assertTrue(myFixture.getLookupElementStrings().containsAll(Arrays.asList("stringProp", "integerProp")));
  }
}
