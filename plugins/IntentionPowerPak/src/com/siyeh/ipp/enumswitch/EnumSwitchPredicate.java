package com.siyeh.ipp.enumswitch;

import com.intellij.psi.*;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.ipp.psiutils.ErrorUtil;

import java.util.HashSet;
import java.util.Set;

class EnumSwitchPredicate implements PsiElementPredicate{
    public boolean satisfiedBy(PsiElement element){
        if(!(element instanceof PsiSwitchStatement)){
            return false;
        }
        if(ErrorUtil.containsError(element)){
            return false;
        }
        final PsiSwitchStatement switchStatement = (PsiSwitchStatement) element;
        final PsiCodeBlock body = switchStatement.getBody();
        if(body == null){
            return false;
        }
        final PsiExpression expression = switchStatement.getExpression();
        if(expression == null){
            return false;
        }
        final PsiType type = expression.getType();
        if(!(type instanceof PsiClassType)){
            return false;
        }
        final PsiClass enumClass = ((PsiClassType) type).resolve();
        if(!enumClass.isEnum()){
            return false;
        }
        final PsiField[] fields = enumClass.getFields();
        final Set<String> enumElements = new HashSet<String>(fields.length);
        for(final PsiField field : fields){
            final PsiType fieldType = field.getType();
            if(fieldType.equals(type)){
                final String fieldName = field.getName();
                enumElements.add(fieldName);
            }
        }
        final PsiStatement[] statements = body.getStatements();
        for(PsiStatement statement : statements){
            if(statement instanceof PsiSwitchLabelStatement){
                final PsiSwitchLabelStatement labelStatement = (PsiSwitchLabelStatement) statement;
                final PsiExpression value = labelStatement.getCaseValue();
                if(value != null){
                    final String valueText = value.getText();
                    enumElements.remove(valueText);
                }
            }
        }
        return enumElements.size() != 0;
    }
}
