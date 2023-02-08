// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.QuickFix;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.uast.*;
import org.jetbrains.uast.visitor.AbstractUastVisitor;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public final class QuickFixGetFamilyNameViolationInspection extends DevKitUastInspectionBase {

  private static final boolean SKIP_CHILDREN = true;

  private final static Set<String> BASE_CONTEXT_AWARE_CLASSES = Set.of(PsiElement.class.getName(),
                                                                       Navigatable.class.getName(),
                                                                       AreaInstance.class.getName(),
                                                                       VirtualFile.class.getName());

  @Override
  public ProblemDescriptor @Nullable [] checkMethod(@NotNull UMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if (isQuickFixGetFamilyNameImplementation(method) && doesMethodViolate(method, new HashSet<>())) {
      UElement anchor = method.getUastAnchor();
      if (anchor == null) return null;
      PsiElement sourcePsi = anchor.getSourcePsi();
      if (sourcePsi == null) return null;
      return new ProblemDescriptor[]{
        manager.createProblemDescriptor(sourcePsi, DevKitBundle.message("inspections.quick.fix.family.name"),
                                        true, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)
      };
    }
    return null;
  }

  private static boolean isQuickFixGetFamilyNameImplementation(@NotNull UMethod method) {
    if (!"getFamilyName".equals(method.getName()) || !method.getUastParameters().isEmpty()) return false;
    if (!method.getJavaPsi().hasModifierProperty(PsiModifier.ABSTRACT)) {
      UClass containingClass = UastContextKt.getUastParentOfType(method.getSourcePsi(), UClass.class, true);
      if (containingClass == null) return false;
      return InheritanceUtil.isInheritor(containingClass.getJavaPsi(), QuickFix.class.getName());
    }
    return false;
  }

  private static boolean doesMethodViolate(@NotNull UMethod method, @NotNull Set<UMethod> processedMethods) {
    PsiMethod methodJavaPsi = method.getJavaPsi();
    if (!processedMethods.add(method) || methodJavaPsi.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (isContextDependentType(method.getReturnType())) {
      return true;
    }
    return checkMethodBody(method, processedMethods);
  }

  private static boolean checkMethodBody(@NotNull UMethod method, @NotNull Set<UMethod> processedMethods) {
    UExpression body = method.getUastBody();
    if (body == null) return false;
    Ref<Boolean> violates = Ref.create(Boolean.FALSE);
    body.accept(new AbstractUastVisitor() {

      @Override
      public boolean visitQualifiedReferenceExpression(@NotNull UQualifiedReferenceExpression node) {
        if (violates.get()) return SKIP_CHILDREN;
        UExpression receiver = node.getReceiver();
        if (receiver instanceof UResolvable resolvable) {
          UElement resolvedUElement = UastContextKt.toUElement(resolvable.resolve());
          if (resolvedUElement != null && isViolation(resolvedUElement)) {
            violates.set(Boolean.TRUE);
            return SKIP_CHILDREN;
          }
        }
        return super.visitQualifiedReferenceExpression(node);
      }

      @Override
      public boolean visitSimpleNameReferenceExpression(@NotNull USimpleNameReferenceExpression node) {
        if (violates.get()) return SKIP_CHILDREN;
        UElement resolvedUElement = UastContextKt.toUElement(node.resolve());
        if (resolvedUElement != null && isViolation(resolvedUElement)) {
          violates.set(Boolean.TRUE);
          return SKIP_CHILDREN;
        }
        return super.visitSimpleNameReferenceExpression(node);
      }

      @Override
      public boolean visitCallExpression(@NotNull UCallExpression node) {
        if (violates.get()) return SKIP_CHILDREN;
        if (node.getKind() == UastCallKind.METHOD_CALL) {
          UElement resolvedUElement = UastContextKt.toUElement(node.resolve());
          if (resolvedUElement != null && isViolation(resolvedUElement)) {
            violates.set(Boolean.TRUE);
            return SKIP_CHILDREN;
          }
        }
        return super.visitCallExpression(node);
      }

      private boolean isViolation(@NotNull UElement uElement) {
        if (uElement instanceof UVariable resolvedUVariable) {
          if (!isStaticField(resolvedUVariable) && isContextDependentType((resolvedUVariable).getType())) {
            return true;
          }
        }
        else if (uElement instanceof UMethod calledMethod) {
          UClass callingContainingClass = UastContextKt.getUastParentOfType(method.getSourcePsi(), UClass.class, true);
          UClass calledMethodClass = UastContextKt.getUastParentOfType(calledMethod.getSourcePsi(), UClass.class, true);
          if (calledMethodClass != null && callingContainingClass != null &&
              (callingContainingClass.getSourcePsi() == calledMethodClass.getSourcePsi() ||
               callingContainingClass.getJavaPsi().isInheritor(calledMethodClass.getJavaPsi(), true))) {
            if (doesMethodViolate(calledMethod, processedMethods)) {
              return true;
            }
          }
        }
        return false;
      }

      private static boolean isStaticField(@NotNull UVariable uVariable) {
        if (!(uVariable instanceof UField)) return false;
        PsiElement variableJavaPsi = uVariable.getJavaPsi();
        if (variableJavaPsi instanceof PsiField field) {
          return field.hasModifierProperty(PsiModifier.STATIC);
        }
        return false;
      }
    });
    return violates.get();
  }

  private static boolean isContextDependentType(@Nullable PsiType type) {
    if (type == null) return false;
    for (String aClass : BASE_CONTEXT_AWARE_CLASSES) {
      if (InheritanceUtil.isInheritor(type, aClass)) {
        return true;
      }
    }
    return false;
  }
}
