/*
 * Copyright 2009-2015 Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ControlFlowUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ListenerMayUseAdapterInspection extends BaseInspection {

  public boolean checkForEmptyMethods = true;

  @Override
  @Nls
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "listener.may.use.adapter.display.name");
  }

  @Override
  @NotNull
  protected String buildErrorString(Object... infos) {
    final PsiClass aClass = (PsiClass)infos[0];
    final String className = aClass.getName();
    final PsiClass adapterClass = (PsiClass)infos[1];
    final String adapterName = adapterClass.getName();
    return InspectionGadgetsBundle.message(
      "listener.may.use.adapter.problem.descriptor", className,
      adapterName);
  }

  @Override
  public JComponent createOptionsPanel() {
    return new SingleCheckboxOptionsPanel(InspectionGadgetsBundle.message(
      "listener.may.use.adapter.emtpy.methods.option"), this,
                                          "checkForEmptyMethods");
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    final PsiClass adapterClass = (PsiClass)infos[1];
    return new ListenerMayUseAdapterFix(adapterClass);
  }

  private static class ListenerMayUseAdapterFix extends InspectionGadgetsFix {

    private final PsiClass adapterClass;

    ListenerMayUseAdapterFix(@NotNull PsiClass adapterClass) {
      this.adapterClass = adapterClass;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "listener.may.use.adapter.quickfix",
        adapterClass.getName());
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Replace with adapter";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiJavaCodeReferenceElement element =
        (PsiJavaCodeReferenceElement)descriptor.getPsiElement();
      final PsiClass aClass = PsiTreeUtil.getParentOfType(element,
                                                          PsiClass.class);
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
        if (!(target instanceof PsiClass)) {
          return;
        }
        final PsiClass interfaceClass = (PsiClass)target;
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
      final PsiElementFactory elementFactory =
        psiFacade.getElementFactory();
      final PsiJavaCodeReferenceElement referenceElement =
        elementFactory.createClassReferenceElement(adapterClass);
      extendsList.add(referenceElement);
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new ListenerMayUseAdapterVisitor();
  }

  private class ListenerMayUseAdapterVisitor extends BaseInspectionVisitor {

    @Override
    public void visitClass(PsiClass aClass) {
      final PsiReferenceList extendsList = aClass.getExtendsList();
      if (extendsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] extendsReferences =
        extendsList.getReferenceElements();
      if (extendsReferences.length > 0) {
        return;
      }
      final PsiReferenceList implementsList = aClass.getImplementsList();
      if (implementsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] implementsReferences =
        implementsList.getReferenceElements();
      for (PsiJavaCodeReferenceElement implementsReference :
        implementsReferences) {
        checkReference(aClass, implementsReference);
      }
    }

    private void checkReference(
      @NotNull PsiClass aClass,
      @NotNull PsiJavaCodeReferenceElement implementsReference) {
      final PsiElement target = implementsReference.resolve();
      if (!(target instanceof PsiClass)) {
        return;
      }
      final PsiClass implementsClass = (PsiClass)target;
      final String className = implementsClass.getQualifiedName();
      if (className == null || !className.endsWith("Listener")) {
        return;
      }
      final String adapterName = className.substring(0,
                                                     className.length() - 8) + "Adapter";
      final JavaPsiFacade psiFacade = JavaPsiFacade.getInstance(
        aClass.getProject());
      final GlobalSearchScope scope =
        implementsClass.getResolveScope();
      final PsiClass adapterClass = psiFacade.findClass(adapterName,
                                                        scope);
      if (adapterClass == null) {
        return;
      }
      if (aClass.equals(adapterClass)) {
        return;
      }
      if (!adapterClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
        return;
      }
      final PsiReferenceList implementsList =
        adapterClass.getImplementsList();
      if (implementsList == null) {
        return;
      }
      final PsiJavaCodeReferenceElement[] referenceElements =
        implementsList.getReferenceElements();
      boolean adapterImplementsListener = false;
      for (PsiJavaCodeReferenceElement referenceElement :
        referenceElements) {
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
      registerError(implementsReference, aClass, adapterClass);
    }
  }
}
