/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

/**
 * @author Dmitry Batkovich
 */
public class QuickFixGetFamilyNameViolationInspection extends DevKitInspectionBase {
  private final static Logger LOG = Logger.getInstance(QuickFixGetFamilyNameViolationInspection.class);

  private final static Set<String> BASE_CONTEXT_AWARE_CLASSES = ContainerUtil.newHashSet(PsiElement.class.getName(),
                                                                                         Navigatable.class.getName(),
                                                                                         AreaInstance.class.getName(),
                                                                                         VirtualFile.class.getName());

  @Nullable
  @Override
  public ProblemDescriptor[] checkMethod(@NotNull PsiMethod method, @NotNull InspectionManager manager, boolean isOnTheFly) {
    if ("getFamilyName".equals(method.getName()) &&
        method.getParameterList().isEmpty() &&
        !method.hasModifierProperty(PsiModifier.ABSTRACT)) {
      final PsiClass aClass = method.getContainingClass();
      if (InheritanceUtil.isInheritor(aClass, QuickFix.class.getName()) && doesMethodViolate(method, new THashSet<>())) {
        final PsiIdentifier identifier = method.getNameIdentifier();
        LOG.assertTrue(identifier != null);
        return new ProblemDescriptor[]{
          manager.createProblemDescriptor(identifier, "QuickFix's getFamilyName() implementation must not depend on a specific context",
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
