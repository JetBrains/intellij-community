// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.WriteActionAware;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.*;

import java.util.Set;

public class ActionIsNotPreviewFriendlyInspection extends DevKitInspectionBase {

  public static final String[] METHODS_TO_IGNORE_CLASS = {
    "generatePreview", "getFileModifierForPreview", "applyFixForPreview", "startInWriteAction"};
  private static final Set<String> ALLOWED_METHOD_LOCATIONS = Set.of(
    LocalQuickFix.class.getName(),
    FileModifier.class.getName(),
    LocalQuickFixOnPsiElement.class.getName(),
    WriteActionAware.class.getName()
  );
  private static final Set<String> ALLOWED_FIELD_TYPES = Set.of(
    String.class.getName(),
    Class.class.getName(),
    Integer.class.getName(),
    Boolean.class.getName()
  );

  @Override
  public @NotNull PsiElementVisitor buildInternalVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    UastLanguagePlugin plugin = UastFacade.INSTANCE.findPlugin(holder.getFile());
    if (plugin == null) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }
    return new PsiElementVisitor() {
      @Override
      public void visitElement(@NotNull PsiElement element) {
        if (element instanceof PsiNameIdentifierOwner) {
          UElement uElement = plugin.convertElementWithParent(element, UClass.class);
          if (uElement instanceof UClass) {
            visitClass((UClass)uElement);
          }
        }
      }

      public void visitClass(@NotNull UClass node) {
        PsiClass psiClass = node.getJavaPsi();
        if (psiClass.isInterface()) return;
        if (!InheritanceUtil.isInheritor(psiClass, LocalQuickFix.class.getName())) return;
        if (hasCustomPreviewStrategy(psiClass)) return;
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
      }

      private boolean hasCustomPreviewStrategy(PsiClass psiClass) {
        for (String methodName : METHODS_TO_IGNORE_CLASS) {
          for (PsiMethod method : psiClass.findMethodsByName(methodName, true)) {
            PsiClass containingClass = method.getContainingClass();
            if (containingClass != null) {
              String className = containingClass.getQualifiedName();
              if (className != null && !ALLOWED_METHOD_LOCATIONS.contains(className)) return true;
            }
          }
        }
        return false;
      }
    };
  }
}
