// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.inspections;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.extensions.AreaInstance;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public final class QuickFixGetFamilyNameViolationInspection extends DevKitInspectionBase {
  private final static Logger LOG = Logger.getInstance(QuickFixGetFamilyNameViolationInspection.class);

  private final static Set<String> BASE_CONTEXT_AWARE_CLASSES = ContainerUtil.newHashSet(PsiElement.class.getName(),
                                                                                         Navigatable.class.getName(),
                                                                                         AreaInstance.class.getName(),
                                                                                         VirtualFile.class.getName());

  @Override
  public ProblemDescriptor @Nullable [] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if ("getFamilyName".equals(method.getName()) &&
        method.getParameterList().isEmpty() &&
        !method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      final PsiClass aClass = method.getContainingClass();
      if (InheritanceUtil.isInheritor(aClass, QuickFix.class.getName()) && doesMethodViolate(method, new HashSet<>())) {
        final PsiIdentifier identifier = method.getNameIdentifier();
        LOG.assertTrue(identifier != null);
        return new ProblemDescriptor[]{
          manager.createProblemDescriptor(identifier, DevKitBundle.message("inspections.quick.fix.family.name"),
                                          (LocalQuickFix)null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING, true)};
      }
    }
    return null;
  }

  private static boolean doesMethodViolate(final PsiMethod method, final Set<PsiMethod> processed) {
    if (!processed.add(method) || method.hasModifierProperty(PsiModifier.STATIC)) return false;
    final PsiCodeBlock body = method.getBody();
    if (body == null) return false;
    if (isContextDependentType(method.getReturnType())) {
      return true;
    }
    final Collection<PsiJavaCodeReferenceElement> referenceIterator =
      PsiTreeUtil.findChildrenOfType(body, PsiJavaCodeReferenceElement.class);
    for (PsiJavaCodeReferenceElement reference : referenceIterator) {

      final PsiElement resolved = reference.resolve();
      if (resolved instanceof PsiVariable) {
        if (!(resolved instanceof PsiField && ((PsiField)resolved).hasModifierProperty(PsiModifier.STATIC)) && isContextDependentType(((PsiVariable)resolved).getType())) {
          return true;
        }
      }

      if (resolved instanceof PsiMethod) {
        final PsiMethod resolvedMethod = (PsiMethod)resolved;
        final PsiClass resolvedContainingClass = resolvedMethod.getContainingClass();
        //if (resolvedMethod.getName().equals("getName") &&
        //    resolvedMethod.getParameterList().getParametersCount() == 0 &&
        //    !resolvedMethod.hasModifierProperty(PsiModifier.STATIC) &&
        //    InheritanceUtil.isInheritor(resolvedContainingClass, QuickFix.class.getName())) {
        //  return true;
        //}
        final PsiClass methodContainingClass = method.getContainingClass();
        if (resolvedContainingClass != null &&
            methodContainingClass != null &&
            (methodContainingClass == resolvedContainingClass ||
             methodContainingClass.isInheritor(resolvedContainingClass, true))) {
          if (doesMethodViolate(resolvedMethod, processed)) {
            return true;
          }
        }
      }
    }
    return false;
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
