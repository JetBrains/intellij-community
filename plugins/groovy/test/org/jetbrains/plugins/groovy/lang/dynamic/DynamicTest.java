// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.dynamic;

import com.intellij.groovy.testFramework.BaseTest;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyProjectDescriptors;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ParamInfo;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DRootElement;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.util.TestUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class DynamicTest implements BaseTest {
  private static final TestFixture<LightJavaProject> project = lightJavaProjectFixture(GroovyProjectDescriptors.GROOVY_2_5);
  private final TestFixture<LightJavaCodeInsightTestFixture> groovy = lightJavaCodeInsightFixture(project);

  @BeforeEach
  void setUpTestDataPath() {
    groovy.get().setTestDataPath(TestUtils.getAbsoluteTestDataPath() + "dynamic");
  }

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  public String getTestName() {
    return groovy.get().getTestName(false);
  }

  @Test
  public void method() {
    getFixture().enableInspections(new GrUnresolvedAccessInspection());
    runInEdtAndWait(() -> getFixture().configureByFile(StringUtil.capitalize(getTestName()) + ".groovy"));
    getFixture().launchAction(getFixture().findSingleIntention("Add dynamic method"));

    GrReferenceExpression referenceExpression = elementUnderCaret(GrReferenceExpression.class);

    runInEdtAndWait(() -> {
      final PsiType[] psiTypes = PsiUtil.getArgumentTypes(referenceExpression, false);
      final String[] methodArgumentsNames = GroovyNamesUtil.getMethodArgumentsNames(getProject(), psiTypes);
      final List<ParamInfo> pairs = QuickfixUtil.swapArgumentsAndTypes(methodArgumentsNames, psiTypes);

      Assertions.assertNotNull(getDClassElement().getMethod(referenceExpression.getReferenceName(), QuickfixUtil.getArgumentsTypes(pairs)));
    });
  }

  @Test
  public void property() {
    getFixture().enableInspections(new GrUnresolvedAccessInspection());
    runInEdtAndWait(() -> getFixture().configureByFile(StringUtil.capitalize(getTestName()) + ".groovy"));
    getFixture().launchAction(getFixture().findSingleIntention("Add dynamic property"));
    GrReferenceExpression referenceExpression = elementUnderCaret(GrReferenceExpression.class);
    runInEdtAndWait(() -> Assertions.assertNotNull(getDClassElement().getPropertyByName(referenceExpression.getReferenceName())));
  }

  @NotNull
  private DClassElement getDClassElement() {
    return EdtTestUtil.runInEdtAndGet(() -> {
      final DRootElement rootElement = DynamicManager.getInstance(getProject()).getRootElement();
      final DClassElement classElement = rootElement.getClassElement(StringUtil.capitalize(getTestName()));
      Assertions.assertNotNull(classElement);
      return classElement;
    });
  }
}
