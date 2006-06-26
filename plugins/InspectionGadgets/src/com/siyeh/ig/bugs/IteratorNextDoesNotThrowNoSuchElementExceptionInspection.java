/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.siyeh.HardcodedMethodConstants;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.psiutils.ExceptionUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

public class IteratorNextDoesNotThrowNoSuchElementExceptionInspection
        extends MethodInspection{

    public String getID(){
        return "IteratorNextCanNotThrowNoSuchElementException";
    }

    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "iterator.next.does.not.throw.nosuchelementexception.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }


    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "iterator.next.does.not.throw.nosuchelementexception.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor(){
        return new IteratorNextDoesNotThrowNoSuchElementExceptionVisitor();
    }

    private static class IteratorNextDoesNotThrowNoSuchElementExceptionVisitor
            extends BaseInspectionVisitor{

        public void visitMethod(@NotNull PsiMethod method){
            // note: no call to super
            @NonNls final String name = method.getName();
            if(!HardcodedMethodConstants.NEXT.equals(name)){
                return;
            }
            if(!method.hasModifierProperty(PsiModifier.PUBLIC)){
                return;
            }
            final PsiParameterList paramList = method.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();
            if(parameters.length != 0){
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null || !IteratorUtils.isIterator(aClass)) {
                return;
            }
            final Set<PsiType> exceptions =
                    ExceptionUtils.calculateExceptionsThrown(method);
            for (final PsiType exception : exceptions) {
                if (exception.equalsToText(
                        "java.util.NoSuchElementException")) {
                    return;
                }
            }
            if(IteratorUtils.callsIteratorNext(method, false)){
                return;
            }
            final CalledMethodsVisitor visitor = new CalledMethodsVisitor();
            method.accept(visitor);
            if (visitor.isNoSuchElementExceptionThrown()) {
                return;
            }
            registerMethodError(method);
        }
    }

    private static class CalledMethodsVisitor
            extends PsiRecursiveElementVisitor {

        private boolean noSuchElementExceptionThrown = false;

        public void visitMethodCallExpression(
                PsiMethodCallExpression expression) {
            if (noSuchElementExceptionThrown) {
                return;
            }
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiElement method = methodExpression.resolve();
            if (method == null) {
                return;
            }
            final Set<PsiType> exceptions =
                    ExceptionUtils.calculateExceptionsThrown(method);
            for (final PsiType exception : exceptions) {
                if (exception.equalsToText(
                        "java.util.NoSuchElementException")) {
                    noSuchElementExceptionThrown = true;
                }
            }
        }

        public boolean isNoSuchElementExceptionThrown() {
            return noSuchElementExceptionThrown;
        }
    }
}