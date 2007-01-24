/*
 * Copyright 2007 Bas Leijdekkers
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

package com.siyeh.ig.jdk15;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ClassUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Vector;

public class EnumerationCanBeIterationInspection extends BaseInspection {

    @NotNull
    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "enumeration.can.be.iteration.display.name");
    }

    @Nls @NotNull
    public String getGroupDisplayName() {
        return GroupNames.JDK15_SPECIFIC_GROUP_NAME;
    }

    @NotNull
    protected String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "enumeration.can.be.iteration.problem.descriptor",
                infos[0]);
    }

    @Nullable
    protected InspectionGadgetsFix buildFix(PsiElement location) {
        //return new EnumerationCanBeIterationFix();
        return null;
    }

    private static class EnumerationCanBeIterationFix
            extends InspectionGadgetsFix {

        @NotNull
        public String getName() {
            return InspectionGadgetsBundle.message(
                    "enumeration.can.be.iteration.quickfix");
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement element = descriptor.getPsiElement();
            final PsiReferenceExpression methodExpression =
                    (PsiReferenceExpression)element.getParent();
            final PsiMethodCallExpression methodCallExpression =
                    (PsiMethodCallExpression)methodExpression.getParent();
            @NonNls final String methodName =
                    methodExpression.getReferenceName();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            final PsiManager manager = element.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            if ("elements".equals(methodName)) {
                if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                        "java.util.Vector")) {
                    final String qualifierText;
                    if (qualifier == null) {
                        qualifierText = "";
                    } else {
                        qualifierText = qualifier.getText() + '.';
                    }
                    final PsiExpression expression =
                            factory.createExpressionFromText(
                                    qualifierText + "iterator()", element);
                    //System.out.println("expression: " + expression);
                    methodCallExpression.replace(expression);
                } else if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                    "java.util.Hashtable")) {

                }
            } else if ("keys".equals(methodName)) {
                if (TypeUtils.expressionHasTypeOrSubtype(qualifier,
                        "java.util.Hashtable")) {

                }
            }
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new EnumerationCanBeIterationVisitor();
    }


    /*
    static void foo(Vector v, Hashtable h) {
        Enumeration e = v.elements();
        while (e.hasMoreElements()) {
            System.out.println(e.nextElement());
        }
        e = h.elements();
        while (e.hasMoreElements()) {
            System.out.println(e.nextElement());
        }
        e = h.keys();
        Iterator i = h.values().iterator();
        while (i.hasNext()) {
            System.out.println(i.next());
        }
    }
    */

    private static class EnumerationCanBeIterationVisitor
            extends BaseInspectionVisitor {

        @NonNls
        private static final String ITERATOR_TEXT = ".iterator()";

        @NonNls
        private static final String KEY_SET_ITERATOR_TEXT = ".keySet().iterator()";

        @NonNls
        private static final String VALUES_ITERATOR_TEXT = ".values().iterator()";

        public void visitMethodCallExpression(PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            @NonNls final String methodName = methodExpression.getReferenceName();
            if (!TypeUtils.expressionHasTypeOrSubtype(expression,
                    "java.util.Enumeration")) {
                return;
            }
            if ("elements".equals(methodName)) {
                final PsiMethod method = expression.resolveMethod();
                if (method == null) {
                    return;
                }
                final PsiClass containingClass = method.getContainingClass();
                if (ClassUtils.isSubclass(containingClass,
                        "java.util.Vector")) {
                    registerMethodCallError(expression, ITERATOR_TEXT);
                } else if (ClassUtils.isSubclass(containingClass,
                        "java.util.Hashteable")) {
                    registerMethodCallError(expression, VALUES_ITERATOR_TEXT);
                }
            } else if ("keys".equals(methodName)) {
                final PsiMethod method = expression.resolveMethod();
                if (method == null) {
                    return;
                }
                final PsiClass containingClass = method.getContainingClass();
                if (ClassUtils.isSubclass(containingClass,
                        "java.util.Hashtable")) {
                    registerMethodCallError(expression, KEY_SET_ITERATOR_TEXT);
                }
            }
        }
    }
}