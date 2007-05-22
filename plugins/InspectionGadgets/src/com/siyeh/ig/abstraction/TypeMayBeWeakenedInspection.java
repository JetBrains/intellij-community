/*
 * Copyright 2006-2007 Bas Leijdekkers
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
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class TypeMayBeWeakenedInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "type.may.be.weakened.display.name");
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        final Collection<PsiClass> weakerClasses =
                (Collection<PsiClass>) infos[1];
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
        final PsiElement element = (PsiElement) infos[0];
        if (element instanceof PsiField) {
            return InspectionGadgetsBundle.message(
                    "type.may.be.weakened.field.problem.descriptor",
                    builder.toString());
        } else if (element instanceof PsiParameter) {
            return InspectionGadgetsBundle.message(
                    "type.may.be.weakened.parameter.problem.descriptor",
                    builder.toString());
        } else if (element instanceof PsiMethod) {
            return InspectionGadgetsBundle.message(
                    "type.may.be.weakened.method.problem.descriptor",
                    builder.toString());
        }
        return InspectionGadgetsBundle.message(
                "type.may.be.weakened.problem.descriptor", builder.toString());
    }

    @Nullable
    protected InspectionGadgetsFix[] buildFixes(PsiElement location) {
        final PsiElement parent = location.getParent();
        final Collection<PsiClass> weakestClasses =
                TypeMayBeWeakenedVisitor.calculateWeakestClassesNecessary(
                        parent);
        if (weakestClasses.isEmpty()) {
            return null;
        }
        final List<InspectionGadgetsFix> fixes = new ArrayList();
        for (PsiClass weakestClass : weakestClasses) {
            final String qualifiedName = weakestClass.getQualifiedName();
            fixes.add(new TypeMayBeWeakenedFix(qualifiedName));
        }
        return fixes.toArray(new InspectionGadgetsFix[fixes.size()]);
    }

    private static class TypeMayBeWeakenedFix extends InspectionGadgetsFix {

        private final String fqClassName;

        TypeMayBeWeakenedFix(String fqClassName) {
            this.fqClassName = fqClassName;
        }

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "type.may.be.weakened.quickfix", fqClassName);
        }

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
            final PsiManager manager = element.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiClassType type =
                    factory.createTypeByFQClassName(fqClassName,
                            element.getResolveScope());
            final PsiTypeElement newTypeElement =
                    factory.createTypeElement(type);
            typeElement.replace(newTypeElement);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new TypeMayBeWeakenedVisitor();
    }

    private static class TypeMayBeWeakenedVisitor
            extends BaseInspectionVisitor {

        public void visitVariable(PsiVariable variable) {
            super.visitVariable(variable);
            if (isOnTheFly() && variable instanceof PsiField) {
                // checking variables with greater visibiltiy is too expensive
                // for error checking in the editor
                if (!variable.hasModifierProperty(PsiModifier.PRIVATE)) {
                    return;
                }
            }
            final Collection<PsiClass> weakestClasses =
                    calculateWeakestClassesNecessary(variable);
            if (weakestClasses.isEmpty()) {
                return;
            }
            registerVariableError(variable, variable, weakestClasses);
        }

        public void visitMethod(PsiMethod method) {
            super.visitMethod(method);
            if (isOnTheFly() &&
                    !method.hasModifierProperty(PsiModifier.PRIVATE)) {
                // checking methods with greater visibility is too expensive.
                // for error checking in the editor
                return;
            }
            final Query<MethodSignatureBackedByPsiMethod> search =
                    SuperMethodsSearch.search(method, null, true, false);
            if (search.findFirst() != null) {
                return;
            }
            final Collection<PsiClass> weakestClasses =
                    calculateWeakestClassesNecessary(method);
            if (weakestClasses.isEmpty()) {
                return;
            }
            registerMethodError(method, method, weakestClasses);
        }

        @NotNull
    public static Collection<PsiClass> calculateWeakestClassesNecessary(
                @NotNull PsiElement variableOrMethod) {
            final PsiType variableOrMethodType;
            if (variableOrMethod instanceof PsiVariable) {
                final PsiVariable variable = (PsiVariable) variableOrMethod;
                variableOrMethodType = variable.getType();
            } else if (variableOrMethod instanceof PsiMethod) {
                final PsiMethod method = (PsiMethod) variableOrMethod;
                variableOrMethodType = method.getReturnType();
            } else {
                throw new IllegalArgumentException(
                        "PsiMethod or PsiVariable expected: " +
                                variableOrMethod);
            }
            if (!(variableOrMethodType instanceof PsiClassType)) {
                return Collections.EMPTY_LIST;
            }
            final PsiClassType variableClassType =
                    (PsiClassType) variableOrMethodType;
            final PsiClass variableClass = variableClassType.resolve();
            if (variableClass == null) {
                return Collections.EMPTY_LIST;
            }
            final PsiManager manager = variableOrMethod.getManager();
            final GlobalSearchScope scope = variableOrMethod.getResolveScope();
            Set<PsiClass> weakestTypeClasses = new HashSet();
            final PsiClass javaLangObjectClass =
                    manager.findClass("java.lang.Object", scope);
            if (variableClass.equals(javaLangObjectClass)) {
                return Collections.EMPTY_LIST;
            }
            weakestTypeClasses.add(javaLangObjectClass);
            final Query<PsiReference> query =
                    ReferencesSearch.search(variableOrMethod);
            for (PsiReference reference : query) {
                if (reference == null) {
                    continue;
                }
                final PsiElement referenceElement = reference.getElement();
                final PsiElement referenceParent = referenceElement.getParent();
                final PsiElement referenceGrandParent =
                        referenceParent.getParent();
                if (referenceParent instanceof PsiExpressionList) {
                    if (!(referenceGrandParent instanceof
                            PsiMethodCallExpression)) {
                        return Collections.EMPTY_LIST;
                    }
                    final PsiMethodCallExpression methodCallExpression =
                            (PsiMethodCallExpression) referenceGrandParent;
                    final PsiReferenceExpression methodExpression =
                            methodCallExpression.getMethodExpression();
                    final PsiElement methodElement = methodExpression.resolve();
                    if (!(methodElement instanceof PsiMethod)) {
                        return Collections.EMPTY_LIST;
                    }
                    final PsiMethod method = (PsiMethod) methodElement;
                    if (!(referenceElement instanceof PsiExpression)) {
                        return Collections.EMPTY_LIST;
                    }
                    final PsiExpression expression =
                            (PsiExpression) referenceElement;
                    final PsiExpressionList expressionList =
                            (PsiExpressionList)referenceParent;
                    final int index =
                            getExpressionIndex(expression, expressionList);
                    final PsiParameterList parameterList =
                            method.getParameterList();
                    final PsiParameter[] parameters =
                            parameterList.getParameters();
                    final PsiParameter parameter;
                    if (index < parameters.length) {
                        parameter = parameters[index];
                    } else {
                        parameter = parameters[parameters.length - 1];
                    }
                    // fixme variable arity methods 
                    final PsiType type = parameter.getType();
                    if (!(type instanceof PsiClassType)) {
                        return Collections.EMPTY_LIST;
                    }
                    final PsiClassType classType = (PsiClassType) type;
                    final PsiClass aClass = classType.resolve();
                    if (aClass == null) {
                        return Collections.EMPTY_LIST;
                    }
                    checkClass(weakestTypeClasses, aClass);
                } else if (referenceGrandParent
                        instanceof PsiMethodCallExpression) {
                    final PsiMethodCallExpression methodCallExpression =
                            (PsiMethodCallExpression) referenceGrandParent;
                    final PsiReferenceExpression methodExpression =
                            methodCallExpression.getMethodExpression();
                    final PsiElement target = methodExpression.resolve();
                    if (!(target instanceof PsiMethod)) {
                        return Collections.EMPTY_LIST;
                    }
                    final PsiMethod method = (PsiMethod) target;
                    final PsiMethod[] superMethods =
                            method.findDeepestSuperMethods();
                    if (superMethods.length > 0) {
                        for (PsiMethod superMethod : superMethods) {
                            final PsiClass containingClass =
                                    superMethod.getContainingClass();
                            checkClass(weakestTypeClasses, containingClass);
                        }
                    } else {
                        final PsiClass containingClass =
                                method.getContainingClass();
                        checkClass(weakestTypeClasses, containingClass);
                    }
                } else if (referenceParent instanceof PsiAssignmentExpression) {
                    final PsiAssignmentExpression assignmentExpression =
                            (PsiAssignmentExpression) referenceParent;
                    final PsiExpression lhs =
                            assignmentExpression.getLExpression();
                    final PsiExpression rhs =
                            assignmentExpression.getRExpression();
                    if (referenceElement.equals(lhs) && rhs != null) {
                        final PsiType type = rhs.getType();
                        if (!(type instanceof PsiClassType)) {
                            return Collections.EMPTY_LIST;
                        }
                        final PsiClassType classType = (PsiClassType) type;
                        final PsiClass aClass = classType.resolve();
                        checkClass(weakestTypeClasses, aClass);
                    } else if (referenceElement.equals(rhs)) {
                        final PsiType type = lhs.getType();
                        if (!(type instanceof PsiClassType)) {
                            return Collections.EMPTY_LIST;
                        }
                        final PsiClassType classType = (PsiClassType) type;
                        final PsiClass aClass = classType.resolve();
                        checkClass(weakestTypeClasses, aClass);
                    }
                }
                if (weakestTypeClasses.contains(variableClass)) {
                    return Collections.EMPTY_LIST;
                }
            }
            weakestTypeClasses =
                    filterVisibleClasses(weakestTypeClasses, variableOrMethod);
            return Collections.unmodifiableCollection(weakestTypeClasses);
        }

        private static Set<PsiClass> filterVisibleClasses(
                Set<PsiClass> weakestTypeClasses, PsiElement context) {
            final Set<PsiClass> result = new HashSet();
            for (PsiClass weakestTypeClass : weakestTypeClasses) {
                if (isVisibleFrom(weakestTypeClass, context)) {
                    result.add(weakestTypeClass);
                    continue;
                }
                final PsiClass visibleInheritor =
                        getVisibleInheritor(weakestTypeClass, context);
                if (visibleInheritor != null) {
                    result.add(visibleInheritor);
                }
            }
            return result;
        }

        @Nullable
        private static PsiClass getVisibleInheritor(PsiClass superClass,
                                                    PsiElement context) {
            final Query<PsiClass> search =
                    DirectClassInheritorsSearch.search(superClass,
                            context.getResolveScope());
            for (PsiClass aClass : search) {
                if (superClass.isInheritor(aClass, true)) {
                    if (isVisibleFrom(aClass, context)) {
                        return aClass;
                    } else {
                        return getVisibleInheritor(aClass, context);
                    }
                }
            }
            return null;
        }

        private static void checkClass(Set<PsiClass> weakestTypeClasses,
                                       PsiClass aClass) {
            boolean shouldAdd = true;
            for (Iterator<PsiClass> iterator =
                    weakestTypeClasses.iterator(); iterator.hasNext();) {
                final PsiClass weakestTypeClass = iterator.next();
                if (!weakestTypeClass.equals(aClass)) {
                    if (aClass.isInheritor(weakestTypeClass, true)) {
                        iterator.remove();
                    } else if (weakestTypeClass.isInheritor(aClass, true)) {
                        shouldAdd = false;
                    }
                } else {
                    shouldAdd = false;
                }
            }
            if (shouldAdd) {
                weakestTypeClasses.add(aClass);
            }
        }

        private static boolean isVisibleFrom(PsiClass aClass,
                                             PsiElement referencingLocation){
            final PsiClass referencingClass =
                    ClassUtils.getContainingClass(referencingLocation);
            if (referencingClass == null){
                return false;
            }
            if(referencingLocation.equals(aClass)){
                return true;
            }
            if(aClass.hasModifierProperty(PsiModifier.PUBLIC)){
                return true;
            }
            if(aClass.hasModifierProperty(PsiModifier.PRIVATE)){
                return false;
            }
            return ClassUtils.inSamePackage(aClass, referencingClass);
        }

        private static int getExpressionIndex(
                @NotNull PsiExpression expression,
                @NotNull PsiExpressionList expressionList) {
            final PsiExpression[] expressions = expressionList.getExpressions();
            for (int i = 0; i < expressions.length; i++) {
                final PsiExpression anExpression = expressions[i];
                if (expression.equals(anExpression)) {
                    return i;
                }
            }
            return -1;
        }
    }
}