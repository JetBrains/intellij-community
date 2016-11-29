/*
 * Copyright 2003-2016 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.DeclarationSearchUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.WeakestTypeFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DeclareCollectionAsInterfaceInspection extends BaseInspection {

  /**
   * @noinspection PublicField
   */
  public boolean ignoreLocalVariables = false;
  /**
   * @noinspection PublicField
   */
  public boolean ignorePrivateMethodsAndFields = false;

  @Override
  @NotNull
  public String getID() {
    return "CollectionDeclaredAsConcreteClass";
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionGadgetsBundle.message(
      "collection.declared.by.class.display.name");
  }

  @Override
  @NotNull
  public String buildErrorString(Object... infos) {
    final String type = (String)infos[0];
    return InspectionGadgetsBundle.message(
      "collection.declared.by.class.problem.descriptor",
      type);
  }

  @Override
  @Nullable
  public JComponent createOptionsPanel() {
    final MultipleCheckboxOptionsPanel optionsPanel =
      new MultipleCheckboxOptionsPanel(this);
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "collection.declared.by.class.ignore.locals.option"),
                             "ignoreLocalVariables");
    optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
      "collection.declared.by.class.ignore.private.members.option"),
                             "ignorePrivateMethodsAndFields");
    return optionsPanel;
  }

  @Override
  protected InspectionGadgetsFix buildFix(Object... infos) {
    return new DeclareCollectionAsInterfaceFix((String)infos[0]);
  }

  private static class DeclareCollectionAsInterfaceFix extends InspectionGadgetsFix {

    private final String typeString;

    DeclareCollectionAsInterfaceFix(String typeString) {
      this.typeString = typeString;
    }

    @Override
    @NotNull
    public String getName() {
      return InspectionGadgetsBundle.message(
        "declare.collection.as.interface.quickfix", typeString);
    }

    @NotNull
    @Override
    public String getFamilyName() {
      return "Weaken type";
    }

    @Override
    protected void doFix(Project project, ProblemDescriptor descriptor)
      throws IncorrectOperationException {
      final PsiElement element = descriptor.getPsiElement();
      final PsiElement parent = element.getParent();
      if (!(parent instanceof PsiJavaCodeReferenceElement)) {
        return;
      }
      final StringBuilder newElementText = new StringBuilder(typeString);
      final PsiJavaCodeReferenceElement referenceElement = (PsiJavaCodeReferenceElement)parent;
      final PsiReferenceParameterList parameterList = referenceElement.getParameterList();
      if (parameterList != null) {
        newElementText.append(parameterList.getText());
      }
      final PsiElement grandParent = parent.getParent();
      if (!(grandParent instanceof PsiTypeElement)) {
        return;
      }
      final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
      final PsiTypeElement newTypeElement = factory.createTypeElementFromText(newElementText.toString(), element);
      JavaCodeStyleManager.getInstance(project).shortenClassReferences(grandParent.replace(newTypeElement));
    }
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new DeclareCollectionAsInterfaceVisitor();
  }

  private class DeclareCollectionAsInterfaceVisitor extends BaseInspectionVisitor {

    @Override
    public void visitVariable(@NotNull PsiVariable variable) {
      if (isOnTheFly() && DeclarationSearchUtils.isTooExpensiveToSearch(variable, false)) {
        return;
      }
      if (ignoreLocalVariables && variable instanceof PsiLocalVariable) {
        return;
      }
      if (ignorePrivateMethodsAndFields) {
        if (variable instanceof PsiField) {
          if (variable.hasModifierProperty(PsiModifier.PRIVATE)) {
            return;
          }
        }
      }
      if (variable instanceof PsiParameter) {
        final PsiParameter parameter = (PsiParameter)variable;
        final PsiElement scope = parameter.getDeclarationScope();
        if (scope instanceof PsiMethod) {
          if (ignorePrivateMethodsAndFields) {
            final PsiMethod method = (PsiMethod)scope;
            if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
              return;
            }
          }
        }
        else if (ignoreLocalVariables) {
          return;
        }
      }
      final PsiType type = variable.getType();
      if (!CollectionUtils.isConcreteCollectionClass(type) || LibraryUtil.isOverrideOfLibraryMethodParameter(variable)) {
        return;
      }

      checkToWeaken(type, variable.getTypeElement(), variable);
    }

    @Override
    public void visitMethod(@NotNull PsiMethod method) {
      super.visitMethod(method);
      if (ignorePrivateMethodsAndFields &&
          method.hasModifierProperty(PsiModifier.PRIVATE)) {
        return;
      }
      if (isOnTheFly() && DeclarationSearchUtils.isTooExpensiveToSearch(method, false)) {
        return;
      }
      final PsiType type = method.getReturnType();
      if (!CollectionUtils.isConcreteCollectionClass(type) || LibraryUtil.isOverrideOfLibraryMethod(method)) {
        return;
      }

      checkToWeaken(type, method.getReturnTypeElement(), method);
    }

    private void checkToWeaken(PsiType type, PsiTypeElement typeElement, PsiElement variable) {
      if (typeElement == null) {
        return;
      }
      final PsiJavaCodeReferenceElement reference = typeElement.getInnermostComponentReferenceElement();
      if (reference == null) {
        return;
      }
      final PsiElement nameElement = reference.getReferenceNameElement();
      if (nameElement == null) {
        return;
      }
      final Collection<PsiClass> weaklings = WeakestTypeFinder.calculateWeakestClassesNecessary(variable, false, true);
      if (weaklings.isEmpty()) {
        return;
      }
      final PsiClassType javaLangObject = PsiType.getJavaLangObject(nameElement.getManager(), nameElement.getResolveScope());
      final List<PsiClass> weaklingList = new ArrayList<>(weaklings);
      final PsiClass objectClass = javaLangObject.resolve();
      weaklingList.remove(objectClass);
      if (weaklingList.isEmpty()) {
        final String typeText = type.getCanonicalText();
        final String interfaceText = CollectionUtils.getInterfaceForClass(typeText);
        if (interfaceText == null) {
          return;
        }
        registerError(nameElement, interfaceText);
      }
      else {
        final PsiClass weakling = weaklingList.get(0);
        final String qualifiedName = weakling.getQualifiedName();
        registerError(nameElement, qualifiedName);
      }
    }
  }
}