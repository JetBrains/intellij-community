// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.uast.UastVisitorAdapter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.UClass;
import org.jetbrains.uast.UElement;
import org.jetbrains.uast.UField;
import org.jetbrains.uast.UastContextKt;
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor;

import java.util.Set;

public class ActionIsNotPreviewFriendlyInspection extends DevKitInspectionBase {

  public static final String[] PREVIEW_METHOD_NAMES = {
    "generatePreview", "getFileModifierForPreview", "applyFixForPreview", "startInWriteAction"};
  private static final Set<String> DEFAULT_PREVIEW_CLASSES = Set.of(
    LocalQuickFix.class.getName(),
    FileModifier.class.getName(),
    LocalQuickFixOnPsiElement.class.getName()
  );
  private static final Set<String> ALLOWED_FIELD_TYPES = Set.of(
    String.class.getName(),
    Class.class.getName(),
    Integer.class.getName(),
    Boolean.class.getName()
  );

  @Override
  public @NotNull PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new UastVisitorAdapter(new AbstractUastNonRecursiveVisitor() {
      @Override
      public boolean visitClass(@NotNull UClass node) {
        PsiClass psiClass = node.getJavaPsi();
        if (psiClass.isInterface()) return true;
        if (!InheritanceUtil.isInheritor(psiClass, LocalQuickFix.class.getName())) return true;
        if (hasCustomPreviewStrategy(psiClass)) return true;
        // PSI mirror of FileModifier#getFileModifierForPreview implementation
        for (PsiField field : psiClass.getFields()) {
          if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
          if (field.hasAnnotation(FileModifier.SafeFieldForPreview.class.getName())) continue;
          PsiType type = field.getType().getDeepComponentType();
          if (type instanceof PsiPrimitiveType) continue;
          if (type instanceof PsiClassType) {
            PsiClass fieldClass = ((PsiClassType)type).resolve();
            if (fieldClass == null) continue;
            if (fieldClass.isEnum()) continue;
            String name = fieldClass.getQualifiedName();
            if (name != null && ALLOWED_FIELD_TYPES.contains(name)) continue;
            UField uField = UastContextKt.toUElement(field, UField.class);
            if (uField == null) continue;
            UElement anchor = uField.getUastAnchor();
            if (anchor == null) continue;
            PsiElement psiAnchor = anchor.getSourcePsi();
            if (psiAnchor == null) continue;
            holder.registerProblem(psiAnchor,
                                   DevKitBundle.message("inspection.message.field.may.prevent.intention.preview.to.work.properly"));
          }
        }
        return true;
      }

      private boolean hasCustomPreviewStrategy(PsiClass psiClass) {
        for (String methodName : PREVIEW_METHOD_NAMES) {
          for (PsiMethod method : psiClass.findMethodsByName(methodName, true)) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
              String className = containingClass.getQualifiedName();
              if (className != null && !DEFAULT_PREVIEW_CLASSES.contains(className)) return true;
            }
          }
        }
        return false;
      }
    }, true);
  }
}
