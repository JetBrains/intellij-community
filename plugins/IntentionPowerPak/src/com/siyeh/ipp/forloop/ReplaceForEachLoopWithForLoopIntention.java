package com.siyeh.ipp.forloop;

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;

public class ReplaceForEachLoopWithForLoopIntention extends Intention{
    public String getText(){
        return "Replace for-each loop with old-style for loop";
    }

    public String getFamilyName(){
        return "Replace For Each Loop";
    }

    public PsiElementPredicate getElementPredicate(){
        return new ForEachLoopPredicate();
    }

    public void processIntention(PsiElement element)
            throws IncorrectOperationException{
        final PsiForeachStatement statement = (PsiForeachStatement) element.getParent();

        final StringBuffer newStatement = new StringBuffer();
        final PsiManager psiManager = statement.getManager();
        final Project project = psiManager.getProject();
        final CodeStyleManager codeStyleManager =
                CodeStyleManager.getInstance(project);
        final PsiExpression iteratedValue = statement.getIteratedValue();
        if(iteratedValue.getType() instanceof PsiArrayType){
            final String index = codeStyleManager.suggestUniqueVariableName("i",
                                                                            statement,
                                                                            true);
            newStatement.append("for(int ");
            newStatement.append(index);
            newStatement.append(" = 0;");
            newStatement.append(index);
            newStatement.append('<');
            newStatement.append(iteratedValue.getText());
            newStatement.append(".length;");
            newStatement.append(index);
            newStatement.append("++)");
            newStatement.append("{ ");
            newStatement.append(statement.getIterationParameter().getType()
                    .getPresentableText());
            newStatement.append(' ');
            newStatement.append(statement.getIterationParameter().getName());
            newStatement.append(" = ");
            newStatement.append(iteratedValue.getText());
            newStatement.append('[');
            newStatement.append(index);
            newStatement.append("];");
            final PsiStatement body = statement.getBody();
            if(body instanceof PsiBlockStatement){
                final PsiCodeBlock block =
                        ((PsiBlockStatement) body).getCodeBlock();
                final PsiElement[] children =
                        block.getChildren();
                for(int i = 1; i < children.length - 1; i++){
                    //skip the braces
                    newStatement.append(children[i].getText());
                }
            } else{
                newStatement.append(body.getText());
            }
            newStatement.append('}');
        } else{

            final String iterator =
                    codeStyleManager.suggestUniqueVariableName("it", statement,
                                                               true);
            final String typeText = statement.getIterationParameter()
                    .getType()
                    .getPresentableText();
            newStatement.append("for(java.util.Iterator<");
            newStatement.append(typeText);
            newStatement.append("> ");
            newStatement.append(iterator);
            newStatement.append(" = ");
            newStatement.append(iteratedValue.getText());
            newStatement.append(".iterator();");
            newStatement.append(iterator);
            newStatement.append(".hasNext();)");
            newStatement.append('{');
            newStatement.append(typeText);
            newStatement.append(' ');
            newStatement.append(statement.getIterationParameter().getName());
            newStatement.append(" = ");
            newStatement.append(iterator);
            newStatement.append(".next();");

            final PsiStatement body = statement.getBody();
            if(body instanceof PsiBlockStatement){
                final PsiCodeBlock block =
                        ((PsiBlockStatement) body).getCodeBlock();
                final PsiElement[] children = block.getChildren();
                for(int i = 1; i < children.length - 1; i++){
                    //skip the braces
                    newStatement.append(children[i].getText());
                }
            } else{
                newStatement.append(body.getText());
            }
            newStatement.append('}');
        }
        replaceStatement(newStatement.toString(), statement);
    }
}
