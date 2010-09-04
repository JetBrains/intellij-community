/*
 * Copyright 2003-2010 Dave Griffith, Bas Leijdekkers
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
package com.siyeh.ig.inheritance;

import com.intellij.codeInspection.ui.SingleCheckboxOptionsPanel;
import com.intellij.psi.*;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.TestUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;

public class RefusedBequestInspection extends BaseInspection {

    /** @noinspection PublicField*/
    public boolean ignoreEmptySuperMethods = false;

    @Override
    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message("refused.bequest.display.name");
    }

    @Override
    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "refused.bequest.problem.descriptor");
    }

    @Override
    public JComponent createOptionsPanel() {
        //noinspection HardCodedStringLiteral
        return new SingleCheckboxOptionsPanel(
                "<html>" + InspectionGadgetsBundle.message(
                        "refused.bequest.ignore.empty.super.methods.option") +
                        "</html>", this, "ignoreEmptySuperMethods");
    }

    @Override
    public BaseInspectionVisitor buildVisitor(){
        return new RefusedBequestVisitor();
    }

    private class RefusedBequestVisitor extends BaseInspectionVisitor{

        @Override public void visitMethod(@NotNull PsiMethod method){
            final PsiCodeBlock body = method.getBody();
            if(body == null){
                return;
            }
            if (method.getNameIdentifier() == null) {
                return;
            }
            final PsiMethod leastConcreteSuperMethod =
                    getLeastConcreteSuperMethod(method);
            if(leastConcreteSuperMethod == null){
                return;
            }
            final PsiClass containingClass =
                    leastConcreteSuperMethod.getContainingClass();
            if (containingClass == null) {
                return;
            }
            final String className = containingClass.getQualifiedName();
            if("java.lang.Object".equals(className)){
                return;
            }
            if (ignoreEmptySuperMethods){
                final PsiMethod navigationElement = (PsiMethod)
                        leastConcreteSuperMethod.getNavigationElement();
                if (MethodUtils.isEmpty(navigationElement)){
                    return;
                }
            }
            if (TestUtils.isJUnit4BeforeOrAfterMethod(method)) {
                return;
            }
            if(containsSuperCall(body, leastConcreteSuperMethod)){
                return;
            }
            registerMethodError(method);
        }

        @Nullable
        private PsiMethod getLeastConcreteSuperMethod(PsiMethod method){
            PsiMethod leastConcreteSuperMethod = null;
            final PsiMethod[] superMethods = method.findSuperMethods(true);
            for(final PsiMethod superMethod : superMethods){
                final PsiClass containingClass =
                        superMethod.getContainingClass();
                if(containingClass != null &&
                   !superMethod.hasModifierProperty(PsiModifier.ABSTRACT) &&
                        !containingClass.isInterface()){
                    leastConcreteSuperMethod = superMethod;
                    return leastConcreteSuperMethod;
                }
            }
            return leastConcreteSuperMethod;
        }

        private boolean containsSuperCall(@NotNull PsiElement context,
                                          @NotNull PsiMethod method){
            final SuperCallVisitor visitor = new SuperCallVisitor(method);
            context.accept(visitor);
            return visitor.hasSuperCall();
        }
    }

    private static class SuperCallVisitor extends JavaRecursiveElementVisitor{

        private final PsiMethod methodToSearchFor;
        private boolean hasSuperCall = false;

        SuperCallVisitor(PsiMethod methodToSearchFor){
            super();
            this.methodToSearchFor = methodToSearchFor;
        }

        @Override public void visitElement(@NotNull PsiElement element){
            if(!hasSuperCall){
                super.visitElement(element);
            }
        }

        @Override public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            if(hasSuperCall){
                return;
            }
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(qualifier == null){
                return;
            }
            final String text = qualifier.getText();
            if(!PsiKeyword.SUPER.equals(text)){
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return;
            }
            if(method.equals(methodToSearchFor)){
                hasSuperCall = true;
            }
        }

        public boolean hasSuperCall(){
            return hasSuperCall;
        }
    }
}
