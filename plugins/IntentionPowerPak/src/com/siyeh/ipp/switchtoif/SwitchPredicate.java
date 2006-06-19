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
package com.siyeh.ipp.switchtoif;

import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

class SwitchPredicate implements PsiElementPredicate{

    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiJavaToken)){
            return false;
        }
        final PsiJavaToken token = (PsiJavaToken) element;
        final IElementType tokenType = token.getTokenType();
        if(!JavaTokenType.SWITCH_KEYWORD.equals(tokenType)){
            return false;
        }
        final PsiElement parent = element.getParent();
        if(!(parent instanceof PsiSwitchStatement)){
            return false;
        }
        final PsiSwitchStatement switchStatement = (PsiSwitchStatement) parent;
        final PsiExpression expression = switchStatement.getExpression();
        if(expression == null || !expression.isValid()){
            return false;
        }
        final PsiCodeBlock body = switchStatement.getBody();
        if(body == null){
            return false;
        }
        if(ErrorUtil.containsError(switchStatement)){
            return false;
        }
        boolean hasLabel = false;
        final PsiStatement[] statements = body.getStatements();
        for(PsiStatement statement : statements){
            if(statement instanceof PsiSwitchLabelStatement){
                hasLabel = true;
                break;
            }
        }
        return hasLabel;
    }
}