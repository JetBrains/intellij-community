/*
 * Copyright 2006-2009 Bas Leijdekkers
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
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.WeakestTypeFinder;
import com.intellij.codeInspection.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.*;

public class TypeMayBeWeakenedInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean useRighthandTypeAsWeakestTypeInAssignments = true;

    @SuppressWarnings({"PublicField"})
    public boolean useParameterizedTypeForCollectionMethods = true;

    @SuppressWarnings({"PublicField"})
    public boolean doNotWeakenToJavaLangObject = true;

    @Override @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "type.may.be.weakened.display.name");
    }

    @Override @NotNull
    protected String buildErrorString(Object... infos) {
        final Iterable<PsiClass> weakerClasses = (Iterable<PsiClass>)infos[1];
        @NonNls final StringBuilder builder = new StringBuilder();
        final Iterator<PsiClass> iterator = weakerClasses.iterator();
        if (iterator.hasNext()) {
            builder.append('\'');
            builder.append(iterator.next().getQualifiedName());
            builder.append('\'');
            while (iterator.hasNext()) {
                builder.append(", '");
                builder.append(iterator.next().getQualifiedName());
                builder.append('\'');
            }
        }
        final Object info = infos[0];
        if (info instanceof PsiField) {
            return InspectionGadgetsBundle.message(
                    "type.may.be.weakened.field.problem.descriptor",
                    builder.toString());
        } else if (info instanceof PsiParameter) {
            return InspectionGadgetsBundle.message(
                    "type.may.be.weakened.parameter.problem.descriptor",
                    builder.toString());
        } else if (info instanceof PsiMethod) {
            return InspectionGadgetsBundle.message(
                    "type.may.be.weakened.method.problem.descriptor",
                    builder.toString());
        }
        return InspectionGadgetsBundle.message(
                "type.may.be.weakened.problem.descriptor", builder.toString());
    }

    @Override @Nullable
    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                        "type.may.be.weakened.ignore.option"),
                "useRighthandTypeAsWeakestTypeInAssignments");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "type.may.be.weakened.collection.method.option"),
                "useParameterizedTypeForCollectionMethods");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "type.may.be.weakened.do.not.weaken.to.object.option"),
                "doNotWeakenToJavaLangObject");
        return optionsPanel;
    }

    @Override @NotNull
    protected InspectionGadgetsFix[] buildFixes(Object... infos) {
        final Iterable<PsiClass> weakerClasses = (Iterable<PsiClass>)infos[1];
        final Collection<InspectionGadgetsFix> fixes = new ArrayList();
        for (PsiClass weakestClass : weakerClasses) {
            final String qualifiedName = weakestClass.getQualifiedName();
            if (qualifiedName == null) {
                continue;
            }
            fixes.add(new TypeMayBeWeakenedFix(qualifiedName));
        }
        return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
    }

    private static class TypeMayBeWeakenedFix extends InspectionGadgetsFix {

        private final String fqClassName;

        TypeMayBeWeakenedFix(@NotNull String fqClassName) {
            this.fqClassName = fqClassName;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "type.may.be.weakened.quickfix", fqClassName);
        }

        @Override
        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiElement parent = element.getParent();
            final PsiTypeElement typeElement;
            if (parent instanceof PsiVariable) {
                final PsiVariable variable =
                        (PsiVariable) parent;
                typeElement = variable.getTypeElement();
            } else if (parent instanceof PsiMethod) {
                final PsiMethod method = (PsiMethod) parent;
                typeElement = method.getReturnTypeElement();
            } else {
                return;
            }
            if (typeElement == null) {
                return;
            }
            final PsiJavaCodeReferenceElement componentReferenceElement =
                    typeElement.getInnermostComponentReferenceElement();
            if (componentReferenceElement == null) {
                return;
            }
            final PsiType oldType = typeElement.getType();
            if (!(oldType instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType)oldType;
            final PsiType[] parameterTypes = classType.getParameters();
            final GlobalSearchScope scope = element.getResolveScope();
            final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
            final PsiClass aClass = facade.findClass(fqClassName, scope);
            if (aClass == null) {
                return;
            }
            final PsiTypeParameter[] typeParameters =
                    aClass.getTypeParameters();
            final PsiElementFactory factory = facade.getElementFactory();
            final PsiClassType type;
            if (typeParameters.length != 0 &&
                    typeParameters.length == parameterTypes.length) {
                final Map<PsiTypeParameter, PsiType> typeParameterMap =
                        new HashMap();
                for (int i = 0; i < typeParameters.length; i++) {
                    final PsiTypeParameter typeParameter = typeParameters[i];
                    final PsiType parameterType = parameterTypes[i];
                    typeParameterMap.put(typeParameter, parameterType);
                }
                final PsiSubstitutor substitutor =
                        factory.createSubstitutor(typeParameterMap);
                type = factory.createType(aClass, substitutor);
            } else {
                type = factory.createTypeByFQClassName(fqClassName, scope);
            }
            final PsiJavaCodeReferenceElement referenceElement =
                    factory.createReferenceElementByType(type);
            componentReferenceElement.replace(referenceElement);
        }
    }

    @Override
    public BaseInspectionVisitor buildVisitor() {
        return new TypeMayBeWeakenedVisitor();
    }

    private class TypeMayBeWeakenedVisitor extends BaseInspectionVisitor {

        @Override public void visitVariable(PsiVariable variable) {
            super.visitVariable(variable);
            if (variable instanceof PsiParameter) {
                final PsiParameter parameter = (PsiParameter)variable;
                final PsiElement declarationScope =
                        parameter.getDeclarationScope();
                if (declarationScope instanceof PsiCatchSection) {
                    // do not weaken catch block parameters
                    return;
                } else if (declarationScope instanceof PsiMethod) {
                    final PsiMethod method = (PsiMethod)declarationScope;
                    final PsiClass containingClass = method.getContainingClass();
                    if (containingClass == null ||
                            containingClass.isInterface()) {
                        return;
                    }
                    final Query<MethodSignatureBackedByPsiMethod> superSearch =
                            SuperMethodsSearch.search(method, null, true, false);
                    if (superSearch.findFirst() != null) {
                        // do not try to weaken parameters of methods with
                        // super methods
                        return;
                    }
                    final Query<PsiMethod> overridingSearch =
                            OverridingMethodsSearch.search(method);
                    if (overridingSearch.findFirst() != null) {
                        // do not try to weaken parameters of methods with
                        // overriding methods.
                        return;
                    }
                }
            }
            if (isOnTheFly() && variable instanceof PsiField) {
                // checking variables with greater visibiltiy is too expensive
                // for error checking in the editor
                if (!variable.hasModifierProperty(PsiModifier.PRIVATE)) {
                    return;
                }
            }
            if (useRighthandTypeAsWeakestTypeInAssignments) {
                if (variable instanceof PsiParameter) {
                    final PsiElement parent = variable.getParent();
                    if (parent instanceof PsiForeachStatement) {
                        final PsiForeachStatement foreachStatement =
                                (PsiForeachStatement) parent;
                        final PsiExpression iteratedValue =
                                foreachStatement.getIteratedValue();
                        if (!(iteratedValue instanceof PsiNewExpression) &&
                                !(iteratedValue instanceof
                                        PsiTypeCastExpression)) {
                            return;
                        }
                    }
                } else {
                    final PsiExpression initializer = variable.getInitializer();
                    if (!(initializer instanceof PsiNewExpression) &&
                            !(initializer instanceof PsiTypeCastExpression)) {
                        return;
                    }
                }
            }
            final Collection<PsiClass> weakestClasses =
                    WeakestTypeFinder.calculateWeakestClassesNecessary(variable,
                            useRighthandTypeAsWeakestTypeInAssignments,
                            useParameterizedTypeForCollectionMethods);
            if (doNotWeakenToJavaLangObject) {
                final Project project = variable.getProject();
                final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
                final PsiClass javaLangObjectClass =
                        facade.findClass("java.lang.Object",
                                variable.getResolveScope());
                weakestClasses.remove(javaLangObjectClass);
            }
            if (weakestClasses.isEmpty()) {
                return;
            }
            registerVariableError(variable, variable, weakestClasses);
        }

        @Override public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            if (isOnTheFly() &&
                    !method.hasModifierProperty(PsiModifier.PRIVATE)) {
                // checking methods with greater visibility is too expensive.
                // for error checking in the editor
                return;
            }
            final Query<MethodSignatureBackedByPsiMethod> superSearch =
                    SuperMethodsSearch.search(method, null, true, false);
            if (superSearch.findFirst() != null) {
                // do not try to weaken methods with super methods
                return;
            }
            final Query<PsiMethod> overridingSearch =
                    OverridingMethodsSearch.search(method);
            if (overridingSearch.findFirst() != null) {
                // do not try to weaken methods with overriding methods.
                return;
            }
            final Collection<PsiClass> weakestClasses =
                    WeakestTypeFinder.calculateWeakestClassesNecessary(method,
                            useRighthandTypeAsWeakestTypeInAssignments,
                            useParameterizedTypeForCollectionMethods);
            if (doNotWeakenToJavaLangObject) {
                final Project project = method.getProject();
                final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
                final PsiClass javaLangObjectClass =
                        facade.findClass("java.lang.Object",
                                method.getResolveScope());
                weakestClasses.remove(javaLangObjectClass);
            }
            if (weakestClasses.isEmpty()) {
                return;
            }
            registerMethodError(method, method, weakestClasses);
        }
    }
}