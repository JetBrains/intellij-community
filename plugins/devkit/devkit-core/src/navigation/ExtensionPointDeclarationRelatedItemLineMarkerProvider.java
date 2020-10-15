// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.ExtensionPoint;
import org.jetbrains.idea.devkit.dom.index.ExtensionPointIndex;
import org.jetbrains.idea.devkit.util.ExtensionPointCandidate;
import org.jetbrains.idea.devkit.util.PluginRelatedLocatorsUtils;

import java.util.Collection;
import java.util.Collections;

/**
 * Navigate from {@code ExtensionPointName} field to declaration in {@code plugin.xml}.
 */
public class ExtensionPointDeclarationRelatedItemLineMarkerProvider extends DevkitRelatedLineMarkerProviderBase {

  @Override
  protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    if (element instanceof PsiField) {
      process((PsiField)element, result);
    }
  }

  private static void process(PsiField psiField, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    if (!isExtensionPointNameDeclarationField(psiField)) return;

    final String epFqn = resolveEpFqn(psiField);
    if (epFqn == null) return;

    final Project project = psiField.getProject();
    final ExtensionPoint point = ExtensionPointIndex.findExtensionPoint(project,
                                                                        PluginRelatedLocatorsUtils.getCandidatesScope(project),
                                                                        epFqn);
    if (point == null) return;

    final ExtensionPointCandidate candidate = new ExtensionPointCandidate(SmartPointerManager.createPointer(point.getXmlTag()), epFqn);
    RelatedItemLineMarkerInfo<PsiElement> info =
      LineMarkerInfoHelper.createExtensionPointLineMarkerInfo(Collections.singletonList(candidate), psiField.getNameIdentifier());
    result.add(info);
  }

  @Nullable
  private static String resolveEpFqn(PsiField psiField) {
    final PsiExpression initializer = psiField.getInitializer();

    PsiExpressionList expressionList = null;
    if (initializer instanceof PsiMethodCallExpression) {
      expressionList = ((PsiMethodCallExpression)initializer).getArgumentList();
    }
    else if (initializer instanceof PsiNewExpression) {
      expressionList = ((PsiNewExpression)initializer).getArgumentList();
    }
    if (expressionList == null) return null;

    final PsiExpression[] expressions = expressionList.getExpressions();
    if (expressions.length != 1) return null;

    final PsiExpression epNameExpression = expressions[0];
    final PsiConstantEvaluationHelper helper = JavaPsiFacade.getInstance(psiField.getProject()).getConstantEvaluationHelper();
    final Object o = helper.computeConstantExpression(epNameExpression);
    return o instanceof String ? (String)o : null;
  }

  private static boolean isExtensionPointNameDeclarationField(PsiField psiField) {
    // *do* allow non-public
    if (!psiField.hasModifierProperty(PsiModifier.FINAL) ||
        !psiField.hasModifierProperty(PsiModifier.STATIC) ||
        psiField.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return false;
    }

    if (!psiField.hasInitializer()) {
      return false;
    }

    final PsiExpression initializer = psiField.getInitializer();
    if (!(initializer instanceof PsiMethodCallExpression) &&
        !(initializer instanceof PsiNewExpression)) {
      return false;
    }

    final PsiClass fieldClass = PsiTypesUtil.getPsiClass(psiField.getType());
    if (fieldClass == null) {
      return false;
    }

    final String qualifiedClassName = fieldClass.getQualifiedName();
    return ExtensionPointName.class.getName().equals(qualifiedClassName) ||
           ProjectExtensionPointName.class.getName().equals(qualifiedClassName);
  }
}
