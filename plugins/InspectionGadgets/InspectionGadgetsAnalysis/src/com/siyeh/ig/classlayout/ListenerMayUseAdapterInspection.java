/*
 * Copyright 2009-2017 Bas Leijdekkers
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
package com.siyeh.ig.classlayout;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.options.OptPane;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInspection.options.OptPane.checkbox;
import static com.intellij.codeInspection.options.OptPane.pane;

public class ListenerMayUseAdapterInspection extends BaseInspection {

  public boolean checkForEmptyMethods = true;

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    final String className = aClass.getName();
    final String adapterName = (String)infos[1];
    return InspectionGadgetsBundle.message(
      "listener.may.use.adapter.problem.descriptor", className,
      adapterName);
  }

  @Override
  public @NotNull OptPane getOptionsPane() {
    return pane(
      checkbox("checkForEmptyMethods", InspectionGadgetsBundle.message("listener.may.use.adapter.emtpy.methods.option")));
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final String adapterName = (String)infos[1];
    return new ListenerMayUseAdapterFix(adapterName);
  }

  private static class ListenerMayUseAdapterFix extends InspectionGadgetsFix {

    private final String adapterName;

    ListenerMayUseAdapterFix(@NotNull String adapterName) {
      this.adapterName = adapterName;
    }

    @Override
    @NotNull
    public String getName() {
      return CommonQuickFixBundle.message("fix.replace.with.x", "extends " + adapterName);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return InspectionGadgetsBundle.message("listener.may.use.adapter.fix.family.name");
    }

    @Override
    protected void doFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
      final PsiJavaCodeReferenceElement element = (PsiJavaCodeReferenceElement)descriptor.getPsiElement();
      final PsiClass aClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
      if (aClass == null) {
        return;
      }
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final PsiMethod[] methods = aClass.getMethods();
      if (methods.length > 0) {
        final PsiElement target = element.resolve();
        if (!(target instanceof PsiClass interfaceClass)) {
          return;
        }
        for (PsiMethod method : methods) {
          if (!ControlFlowUtils.isEmptyCodeBlock(method.getBody())) {
            continue;
          }
          final PsiMethod[] superMethods = method.findSuperMethods(interfaceClass);
          if (superMethods.length > 0) {
            method.delete();
          }
        }
      }
      element.delete();
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(project);
      final PsiClass adapterClass = psiFacade.findClass(adapterName, aClass.getResolveScope());
      if (adapterClass == null) {
        return;
      }
      final PsiJavaCodeReferenceElement referenceElement = psiFacade.getElementFactory().createClassReferenceElement(adapterClass);
      extendsList.add(referenceElement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ListenerMayUseAdapterVisitor();
  }

  private class ListenerMayUseAdapterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(@NotNull PsiClass aClass) {
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] extendsReferences = extendsList.getReferenceElements();
      if (extendsReferences.length > 0) {
        return;
      }
      final PsiReferenceList implementsList = aClass.getImplementsList();
      if (implementsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] implementsReferences = implementsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement implementsReference : implementsReferences) {
        checkReference(aClass, implementsReference);
      }
    }

    private void checkReference(@NotNull PsiClass aClass, @NotNull PsiJavaCodeReferenceElement implementsReference) {
      final PsiElement target = implementsReference.resolve();
      if (!(target instanceof PsiClass implementsClass)) {
        return;
      }
      @NonNls final String className = implementsClass.getQualifiedName();
      if (className == null || !className.endsWith("Listener")) {
        return;
      }
      final PsiMethod[] interfaceMethods = implementsClass.getMethods();
      if (interfaceMethods.length < 2) {
        return;
      }
      boolean allDefault = true;
      for (PsiMethod interfaceMethod : interfaceMethods) {
        if (!interfaceMethod.hasModifierProperty(PsiModifier.DEFAULT)) {
          allDefault = false;
          break;
        }
      }
      if (allDefault) {
        return;
      }
      @NonNls final String adapterName = className.substring(0, className.length() - 8) + "Adapter";
      final GlobalSearchScope scope = implementsClass.getResolveScope();
      final PsiClass adapterClass = JavaPsiFacade.getInstance(aClass.getProject()).findClass(adapterName, scope);
      if (adapterClass == null || adapterClass.equals(aClass) || !adapterClass.hasModifierProperty(PsiModifier.ABSTRACT) ||
        adapterClass.isDeprecated()) {
        return;
      }
      final PsiReferenceList implementsList = adapterClass.getImplementsList();
      if (implementsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] referenceElements = implementsList.getReferenceElements();
      boolean adapterImplementsListener = false;
      for (PsiJavaCodeReferenceElement referenceElement : referenceElements) {
        final PsiElement implementsTarget = referenceElement.resolve();
        if (!implementsClass.equals(implementsTarget)) {
          continue;
        }
        adapterImplementsListener = true;
      }
      if (!adapterImplementsListener) {
        return;
      }
      if (checkForEmptyMethods) {
        boolean emptyMethodFound = false;
        final PsiMethod[] methods = aClass.getMethods();
        for (PsiMethod method : methods) {
          final PsiCodeBlock body = method.getBody();
          if (!ControlFlowUtils.isEmptyCodeBlock(body)) {
            continue;
          }
          final PsiMethod[] superMethods = method.findSuperMethods(implementsClass);
          if (superMethods.length == 0) {
            continue;
          }
          emptyMethodFound = true;
          break;
        }
        if (!emptyMethodFound) {
          return;
        }
      }
      registerError(implementsReference, aClass, adapterName);
    }
  }
}
