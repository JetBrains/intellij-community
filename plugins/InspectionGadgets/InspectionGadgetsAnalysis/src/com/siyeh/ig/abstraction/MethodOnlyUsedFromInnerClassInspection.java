/*
 * Copyright 2005-2012 Bas Leijdekkers
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
package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Processor;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class MethodOnlyUsedFromInnerClassInspection extends BaseInspection {

  @SuppressWarnings({"PublicField"})
  public boolean ignoreMethodsAccessedFromAnonymousClass = false;

  @SuppressWarnings({"PublicField"})
  public boolean ignoreStaticMethodsFromNonStaticInnerClass = false;

  @SuppressWarnings({"PublicField"})
  public boolean onlyReportStaticMethods = false;

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message("method.only.used.from.inner.class.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiNamedElement element = (PsiNamedElement)infos[0];
    final String name = element.getName();
    if (infos.length > 1) {
      if (Boolean.TRUE.equals(infos[1])) {
        return InspectionGadgetsBundle.message("method.only.used.from.inner.class.problem.descriptor.anonymous.extending", name);
      }
      return InspectionGadgetsBundle.message("method.only.used.from.inner.class.problem.descriptor.anonymous.implementing", name);
    }
    return InspectionGadgetsBundle.message(
      "method.only.used.from.inner.class.problem.descriptor", name);
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel panel = new MultipleCheckboxOptionsPanel(this);
    panel.addCheckbox(InspectionGadgetsBundle.message("method.only.used.from.inner.class.ignore.option"),
                      "ignoreMethodsAccessedFromAnonymousClass");
    panel.addCheckbox(InspectionGadgetsBundle.message("ignore.static.methods.accessed.from.a.non.static.inner.class"),
                      "ignoreStaticMethodsFromNonStaticInnerClass");
    panel.addCheckbox(InspectionGadgetsBundle.message("only.report.static.methods"), "onlyReportStaticMethods");
    return panel;
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new MethodOnlyUsedFromInnerClassVisitor();
  }

  private class MethodOnlyUsedFromInnerClassVisitor
    extends BaseInspectionVisitor {

    @Override
    public void visitMethod(PsiMethod method) {
      super.visitMethod(method);
      if (!method.hasModifierProperty(PsiModifier.PRIVATE) || method.isConstructor()) {
        return;
      }
      if (onlyReportStaticMethods && !method.hasModifierProperty(PsiModifier.STATIC)) {
        return;
      }
      if (method.getNameIdentifier() == null) {
        return;
      }
      final MethodReferenceFinder processor = new MethodReferenceFinder(method);
      if (!processor.isOnlyAccessedFromInnerClass()) {
        return;
      }
      final PsiClass containingClass = processor.getContainingClass();
      if (ignoreStaticMethodsFromNonStaticInnerClass && method.hasModifierProperty(PsiModifier.STATIC)) {
        final PsiElement parent = containingClass.getParent();
        if (parent instanceof PsiClass && !containingClass.hasModifierProperty(PsiModifier.STATIC)) {
          return;
        }
      }
      if (containingClass instanceof PsiAnonymousClass) {
        final PsiClass[] interfaces = containingClass.getInterfaces();
        final PsiClass superClass;
        if (interfaces.length == 1) {
          superClass = interfaces[0];
          registerMethodError(method, superClass, Boolean.valueOf(false));
        }
        else {
          superClass = containingClass.getSuperClass();
          if (superClass == null) {
            return;
          }
          registerMethodError(method, superClass, Boolean.valueOf(true));
        }
      }
      else {
        registerMethodError(method, containingClass);
      }
    }
  }

  private class MethodReferenceFinder implements Processor<PsiReference> {

    private final PsiClass methodClass;
    private final PsiMethod method;
    private boolean onlyAccessedFromInnerClass = false;

    private PsiClass cache = null;

    MethodReferenceFinder(@NotNull PsiMethod method) {
      this.method = method;
      methodClass = method.getContainingClass();
    }

    @Override
    public boolean process(PsiReference reference) {
      final PsiElement element = reference.getElement();
      final PsiMethod containingMethod = PsiTreeUtil.getParentOfType(element, PsiMethod.class);
      if (method.equals(containingMethod)) {
        return true;
      }
      final PsiClass containingClass = ClassUtils.getContainingClass(element);
      if (containingClass == null) {
        onlyAccessedFromInnerClass = false;
        return false;
      }
      if (containingClass instanceof PsiAnonymousClass) {
        final PsiAnonymousClass anonymousClass = (PsiAnonymousClass)containingClass;
        final PsiExpressionList argumentList = anonymousClass.getArgumentList();
        if (PsiTreeUtil.isAncestor(argumentList, element, true)) {
          onlyAccessedFromInnerClass = false;
          return false;
        }
        if (ignoreMethodsAccessedFromAnonymousClass) {
          onlyAccessedFromInnerClass = false;
          return false;
        }
      }
      if (cache != null) {
        if (!cache.equals(containingClass)) {
          onlyAccessedFromInnerClass = false;
          return false;
        }
      }
      else if (!PsiTreeUtil.isAncestor(methodClass, containingClass, true)) {
        onlyAccessedFromInnerClass = false;
        return false;
      }
      onlyAccessedFromInnerClass = true;
      cache = containingClass;
      return true;
    }

    public boolean isOnlyAccessedFromInnerClass() {
      final PsiSearchHelper searchHelper = PsiSearchHelper.SERVICE.getInstance(method.getProject());
      final ProgressManager progressManager = ProgressManager.getInstance();
      final ProgressIndicator progressIndicator = progressManager.getProgressIndicator();
      final PsiSearchHelper.SearchCostResult searchCost =
        searchHelper.isCheapEnoughToSearch(method.getName(), method.getResolveScope(), null, progressIndicator);
      if (searchCost == PsiSearchHelper.SearchCostResult.TOO_MANY_OCCURRENCES ||
          searchCost == PsiSearchHelper.SearchCostResult.ZERO_OCCURRENCES) {
        return onlyAccessedFromInnerClass;
      }
      final Query<PsiReference> query = ReferencesSearch.search(method);
      query.forEach(this);
      return onlyAccessedFromInnerClass;
    }

    public PsiClass getContainingClass() {
      return cache;
    }
  }
}