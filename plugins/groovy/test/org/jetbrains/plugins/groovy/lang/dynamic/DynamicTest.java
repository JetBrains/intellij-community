// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.dynamic;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiType;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManager;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ParamInfo;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DClassElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DRootElement;
import org.jetbrains.plugins.groovy.codeInspection.untypedUnresolvedAccess.GrUnresolvedAccessInspection;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.util.BaseTest;
import org.jetbrains.plugins.groovy.util.TestUtils;

import java.util.List;

public class DynamicTest extends JavaCodeInsightFixtureTestCase implements BaseTest {

  @NotNull
  @Override
  public CodeInsightTestFixture getFixture() {
    return myFixture;
  }

  @NotNull
  @Override
  public Project getProject() {
    return super.getProject();
  }

  @Override
  protected String getBasePath() {
    return TestUtils.getTestDataPath() + "dynamic/";
  }

  public void testMethod() {
    myFixture.enableInspections(new GrUnresolvedAccessInspection());
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.launchAction(myFixture.findSingleIntention("Add Dynamic Method"));

    GrReferenceExpression referenceExpression = elementUnderCaret(GrReferenceExpression.class);

    final PsiType[] psiTypes = PsiUtil.getArgumentTypes(referenceExpression, false);
    final String[] methodArgumentsNames = GroovyNamesUtil.getMethodArgumentsNames(getProject(), psiTypes);
    final List<ParamInfo> pairs = QuickfixUtil.swapArgumentsAndTypes(methodArgumentsNames, psiTypes);

    assertNotNull(getDClassElement().getMethod(referenceExpression.getReferenceName(), QuickfixUtil.getArgumentsTypes(pairs)));
  }

  public void testProperty() {
    myFixture.enableInspections(new GrUnresolvedAccessInspection());
    myFixture.configureByFile(getTestName(false) + ".groovy");
    myFixture.launchAction(myFixture.findSingleIntention("Add Dynamic Property"));
    GrReferenceExpression referenceExpression = elementUnderCaret(GrReferenceExpression.class);
    assertNotNull(getDClassElement().getPropertyByName(referenceExpression.getReferenceName()));
  }

  @NotNull
  private DClassElement getDClassElement() {
    final DRootElement rootElement = DynamicManager.getInstance(getProject()).getRootElement();
    final DClassElement classElement = rootElement.getClassElement(getTestName(false));
    assertNotNull(classElement);
    return classElement;
  }
}
