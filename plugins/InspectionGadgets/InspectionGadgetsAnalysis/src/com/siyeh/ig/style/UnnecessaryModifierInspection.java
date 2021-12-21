// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RemoveModifierFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class UnnecessaryModifierInspection extends BaseInspection implements CleanupLocalInspectionTool {

  @Override
  protected @NotNull String buildErrorString(Object... infos) {
    return (String)infos[0];
  }

  @Override
  protected @Nullable InspectionGadgetsFix buildFix(Object... infos) {
    return new RemoveModifierFix((String)infos[0]);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new UnnecessaryModifierVisitor();
  }

  private static class UnnecessaryModifierVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(PsiClass aClass) {
      final PsiElement parent = aClass.getParent();
      final boolean interfaceMember = parent instanceof PsiClass && ((PsiClass)parent).isInterface();
      if (aClass.isRecord() || aClass.isInterface() || aClass.isEnum() || interfaceMember) {
        PsiModifierList modifierList = aClass.getModifierList();
        if (modifierList == null) {
          return;
        }
        final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
        for (PsiKeyword modifier : modifiers) {
          final IElementType tokenType = modifier.getTokenType();
          if (JavaTokenType.FINAL_KEYWORD == tokenType && aClass.isRecord()) {
            // all records are implicitly final
            registerError(modifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                          InspectionGadgetsBundle.message("unnecessary.record.modifier.problem.descriptor"));
          }
          else if (JavaTokenType.ABSTRACT_KEYWORD == tokenType && aClass.isInterface()) {
            // all interfaces are implicitly abstract
            registerError(modifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                          InspectionGadgetsBundle.message("unnecessary.interface.modifier.problem.descriptor"));
          }
          else if (JavaTokenType.STATIC_KEYWORD == tokenType && parent instanceof PsiClass) {
            // all nested interfaces, nested enums, nested records and nested inner classes of interfaces are implicitly static
            if (aClass.isRecord()) {
              registerError(modifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                            InspectionGadgetsBundle.message("unnecessary.inner.record.modifier.problem.descriptor"));
            }
            else if (aClass.isInterface()) {
              registerError(modifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                            InspectionGadgetsBundle.message("unnecessary.inner.interface.modifier.problem.descriptor"));
            }
            else if (aClass.isEnum()) {
              registerError(modifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                            InspectionGadgetsBundle.message("unnecessary.inner.enum.modifier.problem.descriptor"));
            }
            else if (interfaceMember) {
              // all inner classes of interfaces are implicitly static
              registerError(modifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                            InspectionGadgetsBundle.message("unnecessary.interface.inner.class.modifier.problem.descriptor"));
            }
          }
          if (JavaTokenType.PUBLIC_KEYWORD == tokenType && interfaceMember) {
            // all inner classes of interfaces are implicitly public
            registerError(modifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                          InspectionGadgetsBundle.message("unnecessary.interface.inner.class.modifier.problem.descriptor"));
          }
        }
      }
    }

    @Override
    public void visitMethod(PsiMethod method) {
      final PsiClass containingClass = method.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (containingClass.isEnum()) {
        if (!method.isConstructor() || !method.hasModifierProperty(PsiModifier.PRIVATE)) {
          return;
        }
        final PsiModifierList modifierList = method.getModifierList();
        final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
        for (PsiKeyword modifier : modifiers) {
          if (JavaTokenType.PRIVATE_KEYWORD == modifier.getTokenType()) {
            registerError(modifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                          InspectionGadgetsBundle.message("unnecessary.enum.constructor.modifier.problem.descriptor"));
          }
        }
      }
      else if (containingClass.isInterface()) {
        final PsiModifierList modifierList = method.getModifierList();
        final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
        for (PsiKeyword modifier : modifiers) {
          final IElementType tokenType = modifier.getTokenType();
          if (JavaTokenType.PUBLIC_KEYWORD == tokenType || JavaTokenType.ABSTRACT_KEYWORD == tokenType) {
            registerError(modifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                          InspectionGadgetsBundle.message("unnecessary.interface.method.modifier.problem.descriptor"));
          }
        }
      }
    }

    @Override
    public void visitField(@NotNull PsiField field) {
      final PsiClass containingClass = field.getContainingClass();
      if (containingClass == null) {
        return;
      }
      if (!containingClass.isInterface()) {
        return;
      }
      final PsiModifierList modifierList = field.getModifierList();
      final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
      for (PsiKeyword modifier : modifiers) {
        final IElementType tokenType = modifier.getTokenType();
        if (JavaTokenType.PUBLIC_KEYWORD == tokenType ||
            JavaTokenType.STATIC_KEYWORD == tokenType ||
            JavaTokenType.FINAL_KEYWORD == tokenType) {
          registerError(modifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL,
                        InspectionGadgetsBundle.message("unnecessary.interface.field.modifier.problem.descriptor"));
        }
      }
    }
  }
}
