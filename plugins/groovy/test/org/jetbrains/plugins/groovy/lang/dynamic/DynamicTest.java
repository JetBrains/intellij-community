// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.dynamic;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiType;
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
import org.jetbrains.plugins.groovy.util.GroovyLatestTest;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class DynamicTest extends GroovyLatestTest implements BaseTest {
  public DynamicTest() {
    super("dynamic");
  }

  @Test
  public void method() {
    getFixture().enableInspections(new GrUnresolvedAccessInspection());
    getFixture().configureByFile(StringUtil.capitalize(getTestName()) + ".groovy");
    getFixture().launchAction(getFixture().findSingleIntention("Add dynamic method"));

    GrReferenceExpression referenceExpression = elementUnderCaret(GrReferenceExpression.class);

    final PsiType[] psiTypes = PsiUtil.getArgumentTypes(referenceExpression, false);
    final String[] methodArgumentsNames = GroovyNamesUtil.getMethodArgumentsNames(getProject(), psiTypes);
    final List<ParamInfo> pairs = QuickfixUtil.swapArgumentsAndTypes(methodArgumentsNames, psiTypes);

    Assert.assertNotNull(getDClassElement().getMethod(referenceExpression.getReferenceName(), QuickfixUtil.getArgumentsTypes(pairs)));
  }

  @Test
  public void property() {
    getFixture().enableInspections(new GrUnresolvedAccessInspection());
    getFixture().configureByFile(StringUtil.capitalize(getTestName()) + ".groovy");
    getFixture().launchAction(getFixture().findSingleIntention("Add dynamic property"));
    GrReferenceExpression referenceExpression = elementUnderCaret(GrReferenceExpression.class);
    Assert.assertNotNull(getDClassElement().getPropertyByName(referenceExpression.getReferenceName()));
  }

  @NotNull
  private DClassElement getDClassElement() {
    final DRootElement rootElement = DynamicManager.getInstance(getProject()).getRootElement();
    final DClassElement classElement = rootElement.getClassElement(StringUtil.capitalize(getTestName()));
    Assert.assertNotNull(classElement);
    return classElement;
  }
}
