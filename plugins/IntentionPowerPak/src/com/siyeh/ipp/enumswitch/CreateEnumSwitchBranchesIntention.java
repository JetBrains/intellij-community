package com.siyeh.ipp.enumswitch;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;

public class CreateEnumSwitchBranchesIntention extends Intention{
    protected PsiElementPredicate getElementPredicate(){
        return new EnumSwitchPredicate();
    }

    public String getText(){
        return "Create 'switch' branches";
    }

    public String getFamilyName(){
        return "Create Enum Switch Branches";
    }

    public void invoke(Project project, Editor editor, PsiFile file)
            throws IncorrectOperationException{
        if(isFileReadOnly(project, file)){
            return;
        }

        final PsiSwitchStatement switchStatement =
                (PsiSwitchStatement) findMatchingElement(file, editor);
        final PsiCodeBlock body = switchStatement.getBody();
        final PsiExpression switchExpression = switchStatement.getExpression();
        final PsiClassType switchType = (PsiClassType) switchExpression.getType();
        final PsiClass enumClass = switchType.resolve();
        final PsiField[] fields = enumClass.getFields();
        final Set missingEnumElements = new HashSet(fields.length);
        for(int i = 0; i < fields.length; i++){
            final PsiField field = fields[i];
            if(field instanceof PsiEnumConstant){
                missingEnumElements.add(field.getName());
            }
        }
        final PsiStatement[] statements = body.getStatements();
        for(int i = 0; i < statements.length; i++){
            final PsiStatement statement = statements[i];
            if(statement instanceof PsiSwitchLabelStatement){
                final PsiSwitchLabelStatement labelStatement =
                        (PsiSwitchLabelStatement) statement;
                final PsiExpression value = labelStatement.getCaseValue();
                if(value instanceof PsiReferenceExpression){
                  final PsiElement resolved = ((PsiReferenceExpression)value).resolve();
                  if (resolved instanceof PsiEnumConstant) {
                    missingEnumElements.remove(((PsiEnumConstant)resolved).getName());
                  }
                }
            }
        }
        final StringBuffer buffer = new StringBuffer(512);

        buffer.append("switch(");
        buffer.append(switchExpression.getText());
        buffer.append("){");
        final PsiElement[] children = body.getChildren();
        for(int i = 1; i < children.length -1; i++){
            buffer.append(children[i].getText());
        }
        final String[] missingElementsArray = (String[]) missingEnumElements.toArray(new String[missingEnumElements.size()]);
        Arrays.sort(missingElementsArray);

        for(int i = 0; i < missingElementsArray.length; i++){
            buffer.append("case ");
            buffer.append(missingElementsArray[i]);
            buffer.append(": break;");
        }
        buffer.append('}');
        final String newStatement = buffer.toString();
        replaceStatement(project, newStatement, switchStatement);
    }
}
