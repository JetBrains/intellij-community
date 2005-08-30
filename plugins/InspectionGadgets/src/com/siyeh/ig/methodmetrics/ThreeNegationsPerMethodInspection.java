/*
 * Copyright 2003-2005 Dave Griffith
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
package com.siyeh.ig.methodmetrics;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiParameterList;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.MethodInspection;
import com.siyeh.ig.ui.SingleCheckboxOptionsPanel;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ThreeNegationsPerMethodInspection extends MethodInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreInEquals = true;

    public String getID(){
        return "MethodWithMoreThanThreeNegations";
    }
    public String getDisplayName() {
        return "Method with more than three negations";
    }

    public String getGroupDisplayName() {
        return GroupNames.METHODMETRICS_GROUP_NAME;
    }

    public JComponent createOptionsPanel(){
        return new SingleCheckboxOptionsPanel("Ignore negations in equals() methods",
                                              this, "m_ignoreInEquals");
    }

    public String buildErrorString(PsiElement location) {
        final PsiMethod method = (PsiMethod) location.getParent();
        assert method != null;
        final NegationCountVisitor visitor=new NegationCountVisitor();
        method.accept(visitor);
        final int negationCount = visitor.getCount();
        return "#ref contains " + negationCount + " negations #loc";
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ThreeNegationsPerMethodVisitor();
    }

    private class ThreeNegationsPerMethodVisitor extends BaseInspectionVisitor {

        public void visitMethod(@NotNull PsiMethod method) {
            // note: no call to super
            final NegationCountVisitor visitor = new NegationCountVisitor();
            method.accept(visitor);
            final int negationCount = visitor.getCount();
            if (negationCount <= 3) {
                return;
            }
            if(m_ignoreInEquals){
                final String methodName = method.getName();
                if(HardcodedMethodConstants.EQUALS.equals(methodName)) {
                  final PsiParameterList parameterList =
                            method.getParameterList();
                    final PsiParameter[] parameters = parameterList.getParameters();
                    if(parameters != null && parameters.length == 1){
                        return;
                    }
                }
            }
            registerMethodError(method);
        }
    }

}
