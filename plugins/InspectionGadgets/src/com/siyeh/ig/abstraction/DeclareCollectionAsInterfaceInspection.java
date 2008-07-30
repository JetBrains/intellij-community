/*
 * Copyright 2003-2008 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Function;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.CollectionUtils;
import com.siyeh.ig.psiutils.LibraryUtil;
import com.siyeh.ig.psiutils.WeakestTypeFinder;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class DeclareCollectionAsInterfaceInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public boolean ignoreLocalVariables = false;
    /** @noinspection PublicField*/
    public boolean ignorePrivateMethodsAndFields = false;

    @NotNull
    public String getID(){
        return "CollectionDeclaredAsConcreteClass";
    }

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "collection.declared.by.class.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        final String type = (String) infos[0];
        return InspectionGadgetsBundle.message(
                "collection.declared.by.class.problem.descriptor",
                type);
    }

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
        return new DeclareCollectionAsInterfaceFix((String) infos[0]);
    }

    private static class DeclareCollectionAsInterfaceFix
            extends InspectionGadgetsFix {

        private final String typeString;

        public DeclareCollectionAsInterfaceFix(
                String typeString) {
            this.typeString = typeString;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "declare.collection.as.interface.quickfix", typeString);
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            if (!(parent instanceof PsiJavaCodeReferenceElement)) {
                return;
            }
            final StringBuilder newElementText = new StringBuilder(typeString);
            final PsiJavaCodeReferenceElement referenceElement =
                    (PsiJavaCodeReferenceElement) parent;
            final PsiReferenceParameterList parameterList =
                    referenceElement.getParameterList();
            if (parameterList != null) {
                final PsiTypeElement[] typeParameterElements =
                        parameterList.getTypeParameterElements();
              if (typeParameterElements.length > 0) {
                newElementText.append('<');
                newElementText.append(StringUtil.join(Arrays.asList(typeParameterElements), new Function<PsiTypeElement, String>() {
                  public String fun(final PsiTypeElement psiTypeElement) {
                    return psiTypeElement.getText();
                  }
                }, ","));
                newElementText.append('>');
              }
            }
            final PsiElement grandParent = parent.getParent();
            if (!(grandParent instanceof PsiTypeElement)) {
                return;
            }
            final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            final PsiElementFactory factory = facade.getElementFactory();
            final PsiType type = factory.createTypeFromText(
                    newElementText.toString(), element);
            final PsiTypeElement newTypeElement = factory.createTypeElement(
                    type);
            grandParent.replace(newTypeElement);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new DeclareCollectionAsInterfaceVisitor();
    }

    private class DeclareCollectionAsInterfaceVisitor
            extends BaseInspectionVisitor {

        @Override public void visitVariable(@NotNull PsiVariable variable) {
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
                } else if (ignoreLocalVariables) {
                    return;
                }
            }
            final PsiType type = variable.getType();
            if (!CollectionUtils.isCollectionClass(type)) {
                return;
            }
            if (LibraryUtil.isOverrideOfLibraryMethodParameter(variable)) {
                return;
            }
            final PsiTypeElement typeElement = variable.getTypeElement();
            if (typeElement == null) {
                return;
            }
            final PsiJavaCodeReferenceElement reference =
                    typeElement.getInnermostComponentReferenceElement();
            if (reference == null) {
                return;
            }
            final PsiElement nameElement = reference.getReferenceNameElement();
            if (nameElement == null) {
                return;
            }
            final Collection<PsiClass> weaklings =
                    WeakestTypeFinder.calculateWeakestClassesNecessary(variable,
                            false, true);
            if (weaklings.isEmpty()) {
                return;
            }
            final List<PsiClass> weaklingList = new ArrayList(weaklings);
            final PsiManager manager = variable.getManager();
            final GlobalSearchScope scope = variable.getResolveScope();
            final PsiClassType javaLangObject =
                    PsiType.getJavaLangObject(manager, scope);
            final PsiClass objectClass = javaLangObject.resolve();
            weaklingList.remove(objectClass);
            if (weaklingList.isEmpty()) {
                registerError(nameElement, "java.util.Collection");
            } else {
                final PsiClass weakling = weaklingList.get(0);
                final String qualifiedName = weakling.getQualifiedName();
                registerError(nameElement, qualifiedName);
            }
        }

        @Override public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (ignorePrivateMethodsAndFields &&
                    method.hasModifierProperty(PsiModifier.PRIVATE)) {
                return;
            }
            final PsiType type = method.getReturnType();
            if (!CollectionUtils.isCollectionClass(type)) {
                return;
            }
            if (LibraryUtil.isOverrideOfLibraryMethod(method)) {
                return;
            }
            final PsiTypeElement typeElement = method.getReturnTypeElement();
            if (typeElement == null) {
                return;
            }
            final PsiJavaCodeReferenceElement referenceElement =
                    typeElement.getInnermostComponentReferenceElement();
            if (referenceElement == null) {
                return;
            }
            final PsiElement nameElement =
                    referenceElement.getReferenceNameElement();
            if (nameElement == null) {
                return;
            }
            final Collection<PsiClass> weaklings =
                    WeakestTypeFinder.calculateWeakestClassesNecessary(method,
                            false, true);
            if (weaklings.isEmpty()) {
                return;
            }
            final List<PsiClass> weaklingList = new ArrayList(weaklings);
            final PsiManager manager = method.getManager();
            final GlobalSearchScope scope = method.getResolveScope();
            final PsiClassType javaLangObject =
                    PsiType.getJavaLangObject(manager, scope);
            final PsiClass objectClass = javaLangObject.resolve();
            weaklingList.remove(objectClass);
            if (weaklingList.isEmpty()) {
                registerError(nameElement, "java.util.collections.Collection");
            } else {
                final PsiClass weakling = weaklingList.get(0);
                registerError(nameElement, weakling.getQualifiedName());
            }
        }
    }
}