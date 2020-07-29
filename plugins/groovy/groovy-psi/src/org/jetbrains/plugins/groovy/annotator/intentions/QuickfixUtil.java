// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.*;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ParamInfo;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyNamesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.PsiImplUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.intellij.codeInspection.IntentionWrapper.wrapToQuickFixes;

public final class QuickfixUtil {
  @Nullable
  public static PsiClass findTargetClass(GrReferenceExpression refExpr) {
    if (refExpr.getQualifier() == null) {
      return PsiUtil.getContextClass(refExpr);
    }

    PsiType type = PsiImplUtil.getQualifierType(refExpr);

    if (ResolveUtil.resolvesToClass(refExpr.getQualifierExpression())) {
      PsiType classType = ResolveUtil.unwrapClassType(type);
      if (classType != null) {
        type = classType;
      }
    }

    if (!(type instanceof PsiClassType)) return null;
    return ((PsiClassType)type).resolve();
  }

  public static boolean isStaticCall(GrReferenceExpression refExpr) {

    //todo: look more carefully
    GrExpression qualifierExpression = refExpr.getQualifierExpression();

    if (!(qualifierExpression instanceof GrReferenceExpression)) return false;

    GrReferenceExpression referenceExpression = (GrReferenceExpression)qualifierExpression;
    GroovyPsiElement resolvedElement = ResolveUtil.resolveProperty(referenceExpression, referenceExpression.getReferenceName());

    if (resolvedElement == null) return false;
    if (resolvedElement instanceof PsiClass) return true;

    return false;
  }

  public static List<ParamInfo> swapArgumentsAndTypes(String[] names, PsiType[] types) {
    List<ParamInfo> result = new ArrayList<>();

    if (names.length != types.length) return Collections.emptyList();

    for (int i = 0; i < names.length; i++) {
      String name = names[i];
      final PsiType type = types[i];

      result.add(new ParamInfo(name, type.getCanonicalText()));
    }

    return result;
  }

  public static String[] getArgumentsTypes(List<? extends ParamInfo> listOfPairs) {
    final List<String> result = new ArrayList<>();

    if (listOfPairs == null) return ArrayUtilRt.EMPTY_STRING_ARRAY;
    for (ParamInfo listOfPair : listOfPairs) {
      String type = PsiTypesUtil.unboxIfPossible(listOfPair.type);
      result.add(type);
    }

    return ArrayUtilRt.toStringArray(result);
  }

  public static String[] getArgumentsNames(List<? extends ParamInfo> listOfPairs) {
    final ArrayList<String> result = new ArrayList<>();
    for (ParamInfo listOfPair : listOfPairs) {
      String name = listOfPair.name;
      result.add(name);
    }

    return ArrayUtilRt.toStringArray(result);
  }

  public static String shortenType(String typeText) {
    if (typeText == null) return "";
    final int i = typeText.lastIndexOf(".");
    if (i != -1) {
      return typeText.substring(i + 1);
    }
    return typeText;
  }

  public static DynamicElementSettings createSettings(GrReferenceExpression referenceExpression) {
    DynamicElementSettings settings = new DynamicElementSettings();
    final PsiClass containingClass = findTargetClass(referenceExpression);

    assert containingClass != null;
    String className = containingClass.getQualifiedName();
    className = className == null ? containingClass.getContainingFile().getName() : className;

    if (isStaticCall(referenceExpression)) {
      settings.setStatic(true);
    }

    settings.setContainingClassName(className);
    settings.setName(referenceExpression.getReferenceName());

    if (PsiUtil.isCall(referenceExpression)) {
      List<PsiType> unboxedTypes = new ArrayList<>();
      for (PsiType type : PsiUtil.getArgumentTypes(referenceExpression, false)) {
        unboxedTypes.add(TypesUtil.unboxPrimitiveTypeWrapperAndEraseGenerics(type));
      }
      final PsiType[] types = unboxedTypes.toArray(PsiType.createArray(unboxedTypes.size()));
      final String[] names = GroovyNamesUtil.getMethodArgumentsNames(referenceExpression.getProject(), types);
      final List<ParamInfo> infos = swapArgumentsAndTypes(names, types);

      settings.setMethod(true);
      settings.setParams(infos);
    } else {
      settings.setMethod(false);
    }
    return settings;
  }

  public static DynamicElementSettings createSettings(GrArgumentLabel label, PsiClass targetClass) {
    DynamicElementSettings settings = new DynamicElementSettings();

    assert targetClass != null;
    String className = targetClass.getQualifiedName();
    className = className == null ? targetClass.getContainingFile().getName() : className;

    settings.setContainingClassName(className);
    settings.setName(label.getName());

    return settings;
  }

  @NotNull
  public static List<IntentionAction> fixesToIntentions(@NotNull PsiElement highlightElement, LocalQuickFix @NotNull [] fixes) {
    InspectionManager inspectionManager = InspectionManager.getInstance(highlightElement.getProject());
    // dummy problem descriptor, highlight element is only used
    ProblemDescriptor descriptor = inspectionManager.createProblemDescriptor(
      highlightElement, highlightElement, "", ProblemHighlightType.INFORMATION, true, LocalQuickFix.EMPTY_ARRAY
    );
    return ContainerUtil.map(fixes, it -> new LocalQuickFixAsIntentionAdapter(it, descriptor));
  }

  public static LocalQuickFix @NotNull [] intentionsToFixes(@NotNull PsiElement highlightElement, @NotNull List<? extends IntentionAction> actions) {
    return wrapToQuickFixes(actions, highlightElement.getContainingFile()).toArray(LocalQuickFix.EMPTY_ARRAY);
  }
}
