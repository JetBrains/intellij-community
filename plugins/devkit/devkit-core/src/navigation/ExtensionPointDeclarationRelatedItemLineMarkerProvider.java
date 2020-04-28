// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.navigation;

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.KeyedLazyInstance;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.util.ExtensionPointCandidate;
import org.jetbrains.idea.devkit.util.ExtensionPointLocator;

import java.util.Collection;
import java.util.List;

public class ExtensionPointDeclarationRelatedItemLineMarkerProvider extends DevkitRelatedLineMarkerProviderBase {

  @Override
  protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    if (element instanceof PsiField) {
      process((PsiField)element, result);
    }
  }

  private static void process(PsiField psiField, Collection<? super RelatedItemLineMarkerInfo<?>> result) {
    final ExtensionPointType epType = isExtensionPointNameDeclarationField(psiField);
    if (epType == ExtensionPointType.NONE) return;

    final PsiClass epClass = resolveExtensionPointClass(psiField, epType);
    if (epClass == null) return;

    final String epName = resolveEpName(psiField);
    if (epName == null) return;


    ExtensionPointLocator locator = new ExtensionPointLocator(epClass);
    List<ExtensionPointCandidate> targets =
      ContainerUtil.filter(locator.findDirectCandidates(), candidate -> epName.equals(candidate.epName));
    if (targets.isEmpty()) {
      return;
    }

    RelatedItemLineMarkerInfo<PsiElement> info =
      LineMarkerInfoHelper.createExtensionPointLineMarkerInfo(targets, psiField.getNameIdentifier());
    result.add(info);
  }

  @Nullable
  private static PsiClass resolveExtensionPointClass(PsiField psiField,
                                                     ExtensionPointType epType) {
    final PsiType epNameType = PsiUtil.substituteTypeParameter(psiField.getType(),
                                                               epType.qualifiedClassName,
                                                               0, false);
    if (!(epNameType instanceof PsiClassType)) {
      return null;
    }
    PsiClassType classType = (PsiClassType)epNameType;

    final PsiClass typePsiClass = PsiTypesUtil.getPsiClass(classType);
    if (classType.getParameterCount() != 1) {
      return typePsiClass;
    }

    // ExtensionPointName<KeyedLazyInstance<T>>
    if (typePsiClass == null || !KeyedLazyInstance.class.getName().equals(typePsiClass.getQualifiedName())) return null;
    PsiType keyedLazyInstanceType = PsiUtil.substituteTypeParameter(epNameType, typePsiClass, 0, false);
    return PsiTypesUtil.getPsiClass(keyedLazyInstanceType);
  }

  private static String resolveEpName(PsiField psiField) {
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


  enum ExtensionPointType {
    NONE(""),
    APPLICATION(ExtensionPointName.class.getName()),
    PROJECT(ProjectExtensionPointName.class.getName());

    private final String qualifiedClassName;

    ExtensionPointType(String qualifiedClassName) {
      this.qualifiedClassName = qualifiedClassName;
    }

    static ExtensionPointType find(String qualifiedClassName) {
      if (APPLICATION.qualifiedClassName.equals(qualifiedClassName)) return APPLICATION;
      if (PROJECT.qualifiedClassName.equals(qualifiedClassName)) return PROJECT;
      return NONE;
    }
  }

  private static ExtensionPointType isExtensionPointNameDeclarationField(PsiField psiField) {
    // *do* allow non-public
    if (!psiField.hasModifierProperty(PsiModifier.FINAL) ||
        !psiField.hasModifierProperty(PsiModifier.STATIC) ||
        psiField.hasModifierProperty(PsiModifier.ABSTRACT)) {
      return ExtensionPointType.NONE;
    }

    if (!psiField.hasInitializer()) {
      return ExtensionPointType.NONE;
    }

    final PsiExpression initializer = psiField.getInitializer();
    if (!(initializer instanceof PsiMethodCallExpression) &&
        !(initializer instanceof PsiNewExpression)) {
      return ExtensionPointType.NONE;
    }

    final PsiClass fieldClass = PsiTypesUtil.getPsiClass(psiField.getType());
    if (fieldClass == null) {
      return ExtensionPointType.NONE;
    }

    final String qualifiedClassName = fieldClass.getQualifiedName();
    return ExtensionPointType.find(qualifiedClassName);
  }
}
