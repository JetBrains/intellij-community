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
package com.siyeh.ipp.enumswitch;

import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class CreateEnumSwitchBranchesIntention extends Intention{
    @NotNull
    protected PsiElementPredicate getElementPredicate(){
        return new EnumSwitchPredicate();
    }

    public String getText(){
        return "Create 'switch' branches";
    }

    public String getFamilyName(){
        return "Create Enum Switch Branches";
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiSwitchStatement switchStatement =
                (PsiSwitchStatement) element;
        final PsiCodeBlock body = switchStatement.getBody();
        final PsiExpression switchExpression = switchStatement.getExpression();
        final PsiClassType switchType = (PsiClassType) switchExpression.getType();
        final PsiClass enumClass = switchType.resolve();
        final PsiField[] fields = enumClass.getFields();
        final Set<String> missingEnumElements = new HashSet<String>(fields.length);
        for(final PsiField field : fields){
            if(field instanceof PsiEnumConstant){
                missingEnumElements.add(field.getName());
            }
        }
        final PsiStatement[] statements = body.getStatements();
        for(final PsiStatement statement : statements){
            if(statement instanceof PsiSwitchLabelStatement){
                final PsiSwitchLabelStatement labelStatement =
                        (PsiSwitchLabelStatement) statement;
                final PsiExpression value = labelStatement.getCaseValue();
                if(value instanceof PsiReferenceExpression){
                    final PsiElement resolved = ((PsiReference) value).resolve();
                    if(resolved instanceof PsiEnumConstant){
                        missingEnumElements.remove(((PsiEnumConstant) resolved).getName());
                    }
                }
            }
        }
        @NonNls final StringBuffer buffer = new StringBuffer(512);

        buffer.append("switch(");
        buffer.append(switchExpression.getText());
        buffer.append("){");
        final PsiElement[] children = body.getChildren();
        for(int i = 1; i < children.length - 1; i++){
            buffer.append(children[i].getText());
        }
        final String[] missingElementsArray = missingEnumElements.toArray(new String[missingEnumElements.size()]);
        Arrays.sort(missingElementsArray);

        for(String aMissingElementsArray : missingElementsArray){
            buffer.append("case ");
            buffer.append(aMissingElementsArray);
            buffer.append(": break;");
        }
        buffer.append('}');
        final String newStatement = buffer.toString();
        replaceStatement(newStatement, switchStatement);
    }
}
