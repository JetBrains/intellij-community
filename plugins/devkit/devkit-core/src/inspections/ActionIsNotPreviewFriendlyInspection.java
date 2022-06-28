// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.*;
import com.intellij.openapi.application.WriteActionAware;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.*;

import java.util.Set;

public class ActionIsNotPreviewFriendlyInspection extends DevKitUastInspectionBase {

  private static final String[] METHODS_TO_IGNORE_CLASS = {
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
    Boolean.class.getName(),
    Long.class.getName(),
    Byte.class.getName(),
    Short.class.getName(),
    Float.class.getName(),
    Double.class.getName(),
    Character.class.getName()
  );

  public ActionIsNotPreviewFriendlyInspection() {
    super(UClass.class);
  }

  @Override
  public ProblemDescriptor @Nullable [] checkClass(@NotNull UClass node, @NotNull InspectionManager manager, boolean isOnTheFly) {
    PsiElement sourcePsi = node.getSourcePsi();
    if (sourcePsi == null) return ProblemDescriptor.EMPTY_ARRAY;
    PsiClass psiClass = node.getJavaPsi();
    if (psiClass.isInterface()) return ProblemDescriptor.EMPTY_ARRAY;
    if (!InheritanceUtil.isInheritor(psiClass, LocalQuickFix.class.getName())) return ProblemDescriptor.EMPTY_ARRAY;
    if (hasCustomPreviewStrategy(psiClass)) return ProblemDescriptor.EMPTY_ARRAY;
    ProblemsHolder holder = new ProblemsHolder(manager, sourcePsi.getContainingFile(), isOnTheFly);
    // PSI mirror of FileModifier#getFileModifierForPreview implementation
    for (PsiField field : psiClass.getFields()) {
      if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
      boolean safeType = hasSafeType(field);
      PsiAnnotation annotation = field.getAnnotation(FileModifier.SafeFieldForPreview.class.getCanonicalName());
      boolean hasSafeFieldAnnotation = annotation != null;
      if (hasSafeFieldAnnotation != safeType) continue;
      PsiElement psiAnchor = getAnchor(field);
      if (psiAnchor == null) continue;
      if (safeType) {
        PsiElement anchor = getAnchor(annotation);
        if (anchor != null) {
          holder.registerProblem(anchor, DevKitBundle.message("inspection.message.unnecessary.safe.field.annotation"),
                                 new RemoveAnnotationQuickFix(annotation, field));
        }
      } else {
        holder.registerProblem(psiAnchor,
                               DevKitBundle.message("inspection.message.field.may.prevent.intention.preview.to.work.properly"));
      }
    }
    return holder.getResultsArray();
  }

  @Nullable
  private static PsiElement getAnchor(PsiField field) {
    UField uField = UastContextKt.toUElement(field, UField.class);
    if (uField == null) return null;
    UElement anchor = uField.getUastAnchor();
    if (anchor == null) return null;
    return anchor.getSourcePsi();
  }

  @Nullable
  private static PsiElement getAnchor(PsiAnnotation field) {
    UAnnotation uAnnotation = UastContextKt.toUElement(field, UAnnotation.class);
    if (uAnnotation == null) return null;
    return uAnnotation.getSourcePsi();
  }

  private static boolean hasSafeType(PsiField field) {
    PsiType type = field.getType().getDeepComponentType();
    if (type instanceof PsiPrimitiveType) return true;
    if (!(type instanceof PsiClassType)) return false;
    PsiClass fieldClass = ((PsiClassType)type).resolve();
    if (fieldClass == null) return true;
    if (fieldClass.isEnum()) return true;
    if (fieldClass.hasAnnotation(FileModifier.SafeTypeForPreview.class.getCanonicalName())) return true;
    String name = fieldClass.getQualifiedName();
    return name != null && ALLOWED_FIELD_TYPES.contains(name);
  }

  private static boolean hasCustomPreviewStrategy(PsiClass psiClass) {
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
}
