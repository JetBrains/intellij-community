// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.encapsulation;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.PsiUpdateModCommandQuickFix;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.PsiReplacementUtil;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
final class ReturnOfCollectionFieldFix extends PsiUpdateModCommandQuickFix {

  private final String myReplacementText;
  private final String myQualifiedClassName;

  private ReturnOfCollectionFieldFix(@NonNls String replacementText, String qualifiedClassName) {
    myReplacementText = replacementText;
    myQualifiedClassName = qualifiedClassName;
  }

  @Nullable
  public static ReturnOfCollectionFieldFix build(PsiReferenceExpression referenceExpression) {
    final String text = referenceExpression.getText();
    if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_MAP)) {
      if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, "java.util.SortedMap")) {
        return new ReturnOfCollectionFieldFix("java.util.Collections.unmodifiableSortedMap(" + text + ')', "java.util.SortedMap");
      }
      return new ReturnOfCollectionFieldFix("java.util.Collections.unmodifiableMap(" + text + ')', CommonClassNames.JAVA_UTIL_MAP);
    }
    else if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_COLLECTION)) {
      if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_SET)) {
        if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_SORTED_SET)) {
          return new ReturnOfCollectionFieldFix("java.util.Collections.unmodifiableSortedSet(" + text + ')', CommonClassNames.JAVA_UTIL_SORTED_SET);
        }
        return new ReturnOfCollectionFieldFix("java.util.Collections.unmodifiableSet(" + text + ')', CommonClassNames.JAVA_UTIL_SET);
      }
      else if (TypeUtils.expressionHasTypeOrSubtype(referenceExpression, CommonClassNames.JAVA_UTIL_LIST)) {
        return new ReturnOfCollectionFieldFix("java.util.Collections.unmodifiableList(" + text + ')', CommonClassNames.JAVA_UTIL_LIST);
      }
      return new ReturnOfCollectionFieldFix("java.util.Collections.unmodifiableCollection(" + text + ')', "java.util.Collection");
    }
    return null;
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return InspectionGadgetsBundle.message("return.of.collection.field.fix.family.name");
  }

  @Override
  @NotNull
  public String getName() {
    return CommonQuickFixBundle.message("fix.replace.with.x", myReplacementText);
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    if (!(element instanceof PsiReferenceExpression referenceExpression)) {
      return;
    }
    fixContainingMethodReturnType(referenceExpression, updater);
    PsiReplacementUtil.replaceExpressionAndShorten(referenceExpression, myReplacementText);
  }

  private void fixContainingMethodReturnType(PsiReferenceExpression referenceExpression, @NotNull ModPsiUpdater updater) {
    final PsiMethod method = PsiTreeUtil.getParentOfType(referenceExpression, PsiMethod.class, true, PsiLambdaExpression.class);
    if (method == null) {
      return;
    }
    final PsiTypeElement returnTypeElement = method.getReturnTypeElement();
    if (returnTypeElement == null) {
      return;
    }
    final PsiType type = returnTypeElement.getType();
    if (!InheritanceUtil.isInheritor(type, myQualifiedClassName)) {
      return;
    }
    if (!(type instanceof PsiClassType classType)) {
      return;
    }
    final Project project = referenceExpression.getProject();
    final PsiClass aClass = classType.resolve();
    if (aClass == null || myQualifiedClassName.equals(aClass.getQualifiedName())) {
      return;
    }
    final PsiType[] parameters = classType.getParameters();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final String typeText;
    if (parameters.length > 0) {
      final StringBuilder builder = new StringBuilder(myQualifiedClassName);
      builder.append('<');
      boolean comma = false;
      for (PsiType parameter : parameters) {
        if (comma) {
          builder.append(',');
        }
        else {
          comma = true;
        }
        builder.append(parameter.getCanonicalText());
      }
      builder.append('>');
      typeText = builder.toString();
    }
    else {
      typeText = myQualifiedClassName;
    }
    final PsiTypeElement newTypeElement = factory.createTypeElementFromText(typeText, referenceExpression);
    final PsiElement replacement = returnTypeElement.replace(newTypeElement);
    final JavaCodeStyleManager javaCodeStyleManager = JavaCodeStyleManager.getInstance(project);
    javaCodeStyleManager.shortenClassReferences(replacement);
    updater.highlight(replacement);
  }
}
