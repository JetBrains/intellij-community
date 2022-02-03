// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.style;

import com.intellij.codeInspection.CleanupLocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.fixes.RemoveModifierFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

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
      final boolean redundantStrictfp = PsiUtil.isLanguageLevel17OrHigher(aClass) && aClass.hasModifierProperty(PsiModifier.STRICTFP);
      if (aClass.isRecord() || aClass.isInterface() || aClass.isEnum() || interfaceMember || redundantStrictfp) {
        PsiModifierList modifierList = aClass.getModifierList();
        if (modifierList == null) {
          return;
        }
        final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
        for (PsiKeyword modifier : modifiers) {
          final IElementType tokenType = modifier.getTokenType();
          if (JavaTokenType.FINAL_KEYWORD == tokenType && aClass.isRecord()) {
            // all records are implicitly final
            registerError(modifier, "unnecessary.record.modifier.problem.descriptor");
          }
          else if (JavaTokenType.ABSTRACT_KEYWORD == tokenType && aClass.isInterface()) {
            // all interfaces are implicitly abstract
            registerError(modifier, "unnecessary.interface.modifier.problem.descriptor");
          }
          else if (JavaTokenType.STATIC_KEYWORD == tokenType && parent instanceof PsiClass) {
            if (aClass.isRecord()) {
              // all inner records are implicitly static
              registerError(modifier, "unnecessary.inner.record.modifier.problem.descriptor");
            }
            else if (aClass.isInterface()) {
              // all inner interfaces are implicitly static
              registerError(modifier, "unnecessary.inner.interface.modifier.problem.descriptor");
            }
            else if (aClass.isEnum()) {
              // all inner enums are implicitly static
              registerError(modifier, "unnecessary.inner.enum.modifier.problem.descriptor");
            }
            else if (interfaceMember) {
              // all inner classes of interfaces are implicitly static
              registerError(modifier, "unnecessary.interface.inner.class.modifier.problem.descriptor");
            }
          }
          if (JavaTokenType.PUBLIC_KEYWORD == tokenType && interfaceMember) {
            // all members of interfaces are implicitly public
            registerError(modifier, "unnecessary.interface.member.modifier.problem.descriptor");
          }
          if (JavaTokenType.STRICTFP_KEYWORD == tokenType && redundantStrictfp) {
            // all code is strictfp under Java 17 and higher
            registerError(modifier, "unnecessary.strictfp.modifier.problem.descriptor");
          }
        }
      }
    }

    @Override
    public void visitMethod(PsiMethod method) {
      final boolean redundantStrictfp = PsiUtil.isLanguageLevel17OrHigher(method) && method.hasModifierProperty(PsiModifier.STRICTFP);
      if (redundantStrictfp) {
        final PsiModifierList modifierList = method.getModifierList();
        final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
        for (PsiKeyword modifier : modifiers) {
          if (JavaTokenType.STRICTFP_KEYWORD == modifier.getTokenType()) {
            // all code is strictfp under Java 17 and higher
            registerError(modifier, "unnecessary.strictfp.modifier.problem.descriptor");
          }
        }
      }
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
            // enum constructors are implicitly private
            registerError(modifier, "unnecessary.enum.constructor.modifier.problem.descriptor");
          }
        }
      }
      else if (containingClass.isInterface()) {
        final PsiModifierList modifierList = method.getModifierList();
        final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
        for (PsiKeyword modifier : modifiers) {
          final IElementType tokenType = modifier.getTokenType();
          if (JavaTokenType.PUBLIC_KEYWORD == tokenType) {
            // all members of interface are implicitly public
            registerError(modifier, "unnecessary.interface.member.modifier.problem.descriptor");
          }
          else if (JavaTokenType.ABSTRACT_KEYWORD == tokenType) {
            // all non-default, non-static methods of interfaces are implicitly abstract
            registerError(modifier, "unnecessary.interface.method.modifier.problem.descriptor");
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
      if (containingClass.isInterface()) {
        final PsiModifierList modifierList = field.getModifierList();
        final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
        for (PsiKeyword modifier : modifiers) {
          final IElementType tokenType = modifier.getTokenType();
          if (JavaTokenType.PUBLIC_KEYWORD == tokenType) {
            // all members of interfaces are implicitly public
            registerError(modifier, "unnecessary.interface.member.modifier.problem.descriptor");
          }
          else if (JavaTokenType.STATIC_KEYWORD == tokenType || JavaTokenType.FINAL_KEYWORD == tokenType) {
            // all fields of interfaces are implicitly static and final
            registerError(modifier, "unnecessary.interface.field.modifier.problem.descriptor");
          }
        }
      }
      else {
        // transient on interface field is a compile error
        if (field.hasModifierProperty(PsiModifier.STATIC) && field.hasModifierProperty(PsiModifier.TRANSIENT)) {
          final PsiModifierList modifierList = field.getModifierList();
          final List<PsiKeyword> modifiers = PsiTreeUtil.getChildrenOfTypeAsList(modifierList, PsiKeyword.class);
          for (PsiKeyword modifier : modifiers) {
            // a transient modifier on a static field is a no-op
            if (JavaTokenType.TRANSIENT_KEYWORD == modifier.getTokenType()) {
              registerError(modifier, "unnecessary.transient.modifier.problem.descriptor");
            }
          }
        }
      }
    }

    private void registerError(@NotNull PsiKeyword modifier,
                               @NotNull @PropertyKey(resourceBundle = InspectionGadgetsBundle.BUNDLE) String key) {
      registerError(modifier, ProblemHighlightType.LIKE_UNUSED_SYMBOL, InspectionGadgetsBundle.message(key));
    }
  }
}
