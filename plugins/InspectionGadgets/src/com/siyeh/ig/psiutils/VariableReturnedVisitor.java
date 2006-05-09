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
package com.siyeh.ig.psiutils;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.PsiReturnStatement;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;

public class VariableReturnedVisitor extends PsiRecursiveElementVisitor {

    private boolean returned = false;
    @NotNull private final PsiVariable variable;

    public VariableReturnedVisitor(@NotNull PsiVariable variable) {
        super();
        this.variable = variable;
    }

    public void visitReturnStatement(
            @NotNull PsiReturnStatement returnStatement) {
        if(returned){
            return;
        }
        super.visitReturnStatement(returnStatement);
        final PsiExpression returnValue = returnStatement.getReturnValue();
        if(VariableAccessUtils.mayEvaluateToVariable(returnValue, variable)) {
            returned = true;
        }
    }

    public boolean isReturned() {
        return returned;
    }
}