// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.dynamic;

import com.intellij.groovy.testFramework.BaseTest;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.intellij.testFramework.EdtTestUtil.runInEdtAndWait;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.testNameFixture;
import static org.jetbrains.plugins.groovy.util.GroovyFixture.groovyFixture;

@TestApplication
public class DynamicTest implements BaseTest {
  private final TestFixture<JavaCodeInsightTestFixture> groovy = groovyFixture(GroovyProjectDescriptors.GROOVY_2_5, "dynamic");
  private final TestFixture<String> testName = testNameFixture(false);

  @Override
  public @NotNull JavaCodeInsightTestFixture getFixture() {
    return groovy.get();
  }

  public String getTestName() {
    return testName.get();
  }

  @Test
  public void method() {
    runInEdtAndWait(() -> {
      getFixture().enableInspections(new GrUnresolvedAccessInspection());
      getFixture().configureByFile(StringUtil.capitalize(getTestName()) + ".groovy");
      getFixture().launchAction(getFixture().findSingleIntention("Add dynamic method"));

      GrReferenceExpression referenceExpression = elementUnderCaret(GrReferenceExpression.class);

      final PsiType[] psiTypes = PsiUtil.getArgumentTypes(referenceExpression, false);
      final String[] methodArgumentsNames = GroovyNamesUtil.getMethodArgumentsNames(getProject(), psiTypes);
      final List<ParamInfo> pairs = QuickfixUtil.swapArgumentsAndTypes(methodArgumentsNames, psiTypes);

      Assertions.assertNotNull(getDClassElement().getMethod(referenceExpression.getReferenceName(), QuickfixUtil.getArgumentsTypes(pairs)));
    });
  }

  @Test
  public void property() {
    runInEdtAndWait(() -> {
      getFixture().enableInspections(new GrUnresolvedAccessInspection());
      getFixture().configureByFile(StringUtil.capitalize(getTestName()) + ".groovy");
      getFixture().launchAction(getFixture().findSingleIntention("Add dynamic property"));
      GrReferenceExpression referenceExpression = elementUnderCaret(GrReferenceExpression.class);
      Assertions.assertNotNull(getDClassElement().getPropertyByName(referenceExpression.getReferenceName()));
    });
  }

  @NotNull
  private DClassElement getDClassElement() {
    final DRootElement rootElement = DynamicManager.getInstance(getProject()).getRootElement();
    final DClassElement classElement = rootElement.getClassElement(StringUtil.capitalize(getTestName()));
    Assertions.assertNotNull(classElement);
    return classElement;
  }
}
