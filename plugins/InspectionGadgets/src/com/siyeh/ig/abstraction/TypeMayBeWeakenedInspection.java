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
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.psi.search.searches.OverridingMethodsSearch;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.SuperMethodsSearch;
import com.intellij.psi.util.MethodSignatureBackedByPsiMethod;
import com.intellij.psi.util.PsiTreeUtil;
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

import javax.swing.JComponent;
import java.util.*;

public class TypeMayBeWeakenedInspection extends BaseInspection {

    @SuppressWarnings({"PublicField"})
    public boolean ignoreLocals = false;

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
    public JComponent createOptionsPanel() {
        //return new SingleCheckboxOptionsPanel(
        //        "Ignore local variables and parameters", this, "ignoreLocals");
        return null;
    }

    @Nullable
    protected InspectionGadgetsFix[] buildFixes(PsiElement location) {
        final PsiElement parent = location.getParent();
        final Collection<PsiClass> weakestClasses =
                calculateWeakestClassesNecessary(
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
            final PsiType oldType = typeElement.getType();
            if (!(oldType instanceof PsiClassType)) {
                return;
            }
            final PsiClassType classType = (PsiClassType)oldType;
            final PsiType[] parameterTypes = classType.getParameters();
            final PsiManager manager = element.getManager();
            final GlobalSearchScope scope = element.getResolveScope();
            final PsiClass aClass = manager.findClass(fqClassName, scope);
            if (aClass == null) {
                return;
            }
            final PsiTypeParameter[] typeParameters =
                    aClass.getTypeParameters();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiClassType type;
            if (typeParameters.length != 0 &&
                    typeParameters.length == parameterTypes.length) {
                final Map<PsiTypeParameter, PsiType> typeParameterMap =
                        new HashMap();
                for (int i = 0; i < typeParameters.length; i++) {
                    PsiTypeParameter typeParameter = typeParameters[i];
                    PsiType parameterType = parameterTypes[i];
                    typeParameterMap.put(typeParameter, parameterType);
                }
                final PsiSubstitutor substitutor =
                        factory.createSubstitutor(typeParameterMap);
                type = factory.createType(aClass, substitutor);
            } else {
                type = factory.createTypeByFQClassName(fqClassName, scope);
            }
            final PsiTypeElement newTypeElement =
                    factory.createTypeElement(type);
            typeElement.replace(newTypeElement);
        }
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
        boolean hasUsages = false;
        for (PsiReference reference : query) {
            if (reference == null) {
                continue;
            }
            hasUsages = true;
            PsiElement referenceElement = reference.getElement();
            PsiElement referenceParent = referenceElement.getParent();
            if (referenceParent instanceof PsiMethodCallExpression) {
                referenceElement = referenceParent;
                referenceParent = referenceElement.getParent();
            }
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
                if (referenceElement.equals(rhs)) {
                    final PsiType type = lhs.getType();
                    if (!(type instanceof PsiClassType)) {
                        return Collections.EMPTY_LIST;
                    }
                    final PsiClassType classType = (PsiClassType) type;
                    final PsiClass aClass = classType.resolve();
                    if (aClass == null) {
                        return Collections.EMPTY_LIST;
                    }
                    checkClass(weakestTypeClasses, aClass);
                }
            } else if (referenceParent instanceof PsiVariable) {
                final PsiVariable variable = (PsiVariable)referenceParent;
                final PsiType type = variable.getType();
                if (!(type instanceof PsiClassType)) {
                    return Collections.EMPTY_LIST;
                }
                final PsiClassType classType = (PsiClassType)type;
                final PsiClass aClass = classType.resolve();
                if (aClass == null) {
                    return Collections.EMPTY_LIST;
                }
                checkClass(weakestTypeClasses, aClass);
            } else if (referenceParent instanceof PsiForeachStatement) {
                final PsiForeachStatement foreachStatement =
                        (PsiForeachStatement)referenceParent;
                if (foreachStatement.getIteratedValue() != referenceElement) {
                    return Collections.EMPTY_LIST;
                }
                final PsiClass javaLangIterableClass =
                        manager.findClass("java.lang.Iterable", scope);
                if (javaLangIterableClass == null) {
                    return Collections.EMPTY_LIST;
                }
                checkClass(weakestTypeClasses, javaLangIterableClass);
            } else if (referenceParent instanceof PsiReturnStatement) {
                final PsiMethod containingMethod =
                        PsiTreeUtil.getParentOfType(referenceParent,
                                PsiMethod.class);
                if (containingMethod == null) {
                    return Collections.EMPTY_LIST;
                }
                final PsiType type = containingMethod.getReturnType();
                if (!(type instanceof PsiClassType)) {
                    return Collections.EMPTY_LIST;
                }
                final PsiClassType classType = (PsiClassType)type;
                final PsiClass aClass = classType.resolve();
                if (aClass == null) {
                    return Collections.EMPTY_LIST;
                }
                checkClass(weakestTypeClasses, aClass);
            } else if (referenceParent instanceof PsiReferenceExpression) {
                // field access, method call is handled above.
                final PsiReferenceExpression referenceExpression =
                        (PsiReferenceExpression)referenceParent;
                final PsiElement target = referenceExpression.resolve();
                if (!(target instanceof PsiField)) {
                    return Collections.EMPTY_LIST;
                }
                final PsiField field = (PsiField)target;
                final PsiClass containingClass = field.getContainingClass();
                checkClass(weakestTypeClasses, containingClass);
            }
            if (weakestTypeClasses.contains(variableClass)) {
                return Collections.EMPTY_LIST;
            }
        }
        if (!hasUsages) {
            return Collections.EMPTY_LIST;
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

    private static void checkClass(
            @NotNull Collection<PsiClass> weakestTypeClasses,
            @NotNull PsiClass aClass) {
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

    public BaseInspectionVisitor buildVisitor() {
        return new TypeMayBeWeakenedVisitor();
    }

    private class TypeMayBeWeakenedVisitor extends BaseInspectionVisitor {

        public void visitVariable(PsiVariable variable) {
            super.visitVariable(variable);
            if (variable instanceof PsiParameter) {
                final PsiParameter parameter = (PsiParameter)variable;
                final PsiElement declarationScope =
                        parameter.getDeclarationScope();
                if (declarationScope instanceof PsiCatchSection) {
                    // do not weaken cast block parameters
                    return;
                } else if (declarationScope instanceof PsiMethod) {
                    final PsiMethod method = (PsiMethod)declarationScope;
                    if (method.getContainingClass().isInterface()) {
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
                }
            }
            if (isOnTheFly() && variable instanceof PsiField) {
                // checking variables with greater visibiltiy is too expensive
                // for error checking in the editor
                if (!variable.hasModifierProperty(PsiModifier.PRIVATE)) {
                    return;
                }
            }
            if (ignoreLocals) {
                if (variable instanceof PsiLocalVariable) {
                    return;
                } else if (variable instanceof PsiParameter) {
                    final PsiParameter parameter = (PsiParameter)variable;
                    final PsiElement scope = parameter.getDeclarationScope();
                    if (!(scope instanceof PsiMethod)) {
                        return;
                    }
                    final PsiMethod method = (PsiMethod)scope;
                    if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
                        return;
                    }
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
                    calculateWeakestClassesNecessary(method);
            if (weakestClasses.isEmpty()) {
                return;
            }
            registerMethodError(method, method, weakestClasses);
        }
    }
}