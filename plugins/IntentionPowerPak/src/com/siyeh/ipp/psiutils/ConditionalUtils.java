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
package com.siyeh.ipp.psiutils;

import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;

public class ConditionalUtils{

    private ConditionalUtils(){
        super();
    }

    public static PsiStatement stripBraces(PsiStatement branch){
        if(branch instanceof PsiBlockStatement){
            final PsiBlockStatement block = (PsiBlockStatement) branch;
            final PsiCodeBlock codeBlock = block.getCodeBlock();
            final PsiStatement[] statements = codeBlock.getStatements();
            if(statements.length == 1){
                return statements[0];
            } else{
                return block;
            }
        } else{
            return branch;
        }
    }

    public static boolean isReturn(PsiStatement statement, @NonNls String value){
        if(statement == null){
            return false;
        }
        if(!(statement instanceof PsiReturnStatement)){
            return false;
        }
        final PsiReturnStatement returnStatement =
                (PsiReturnStatement) statement;
        if(returnStatement.getReturnValue() == null){
            return false;
        }
        final PsiExpression returnValue = returnStatement.getReturnValue();
	    if (returnValue == null){
		    return false;
	    }
        final String returnValueText = returnValue.getText();
        return value.equals(returnValueText);
    }

    public static boolean isAssignment(PsiStatement statement, @NonNls String value){
        if(statement == null){
            return false;
        }
        if(!(statement instanceof PsiExpressionStatement)){
            return false;
        }
        final PsiExpressionStatement expressionStatement =
                (PsiExpressionStatement) statement;
        final PsiExpression expression = expressionStatement.getExpression();
        if(!(expression instanceof PsiAssignmentExpression)){
            return false;
        }
        final PsiAssignmentExpression assignment =
                (PsiAssignmentExpression) expression;
        final PsiExpression rhs = assignment.getRExpression();
        if(rhs == null){
            return false;
        }
        final String rhsText = rhs.getText();
        return value.equals(rhsText);
    }

    public static boolean isAssignment(PsiStatement statement){
        if(!(statement instanceof PsiExpressionStatement)){
            return false;
        }
        final PsiExpressionStatement expressionStatement =
                (PsiExpressionStatement) statement;
        final PsiExpression expression = expressionStatement.getExpression();
        return expression instanceof PsiAssignmentExpression;
    }
}
