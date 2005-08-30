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
package com.siyeh.ig.jdk;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

public class ForeachStatementInspection extends StatementInspection{
    private final ForEachFix fix = new ForEachFix();

    public String getDisplayName(){
        return InspectionGadgetsBundle.message("extended.for.statement.display.name");
    }

    public String getGroupDisplayName(){
        return GroupNames.JDK_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return InspectionGadgetsBundle.message("extended.for.statement.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class ForEachFix extends InspectionGadgetsFix{
        public String getName(){
            return InspectionGadgetsBundle.message("extended.for.statement.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiForeachStatement statement =
                    (PsiForeachStatement) descriptor.getPsiElement()
                            .getParent();

            final CodeStyleManager codeStyleManager =
                    CodeStyleManager.getInstance(project);
            assert statement != null;
            @NonNls final StringBuffer newStatement=new StringBuffer();
            final PsiExpression iteratedValue = statement.getIteratedValue();
            if(iteratedValue.getType() instanceof PsiArrayType){
                final String index = codeStyleManager.suggestUniqueVariableName("i",
                                                                                statement,
                                                                                true);
                newStatement.append("for(int " + index + " = 0;" + index + '<');
                newStatement.append(iteratedValue.getText());
                newStatement.append(".length;" + index + "++)");
                newStatement.append("{ ");
                newStatement.append(statement.getIterationParameter().getType()
                                            .getPresentableText());
                newStatement.append(' ');
                newStatement.append(statement.getIterationParameter()
                                            .getName());
                newStatement.append(" = ");
                newStatement.append(iteratedValue.getText());
                newStatement.append('[' + index + "];");
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

                final String iterator =  codeStyleManager.suggestUniqueVariableName("it", statement,
                                                                                    true);
                final String typeText = statement.getIterationParameter()
                                .getType()
                                .getPresentableText();
                newStatement.append("for(java.util.Iterator<");
                newStatement.append(typeText);
                newStatement.append("> " + iterator + " = ");
                newStatement.append(iteratedValue.getText());
                newStatement.append(".iterator();" + iterator + ".hasNext();)");
                newStatement.append('{');
                newStatement.append(typeText);
                newStatement.append(' ');
                newStatement.append(statement.getIterationParameter()
                                            .getName());
                newStatement.append(" = ");
                newStatement.append(iterator + ".next();");

                final PsiStatement body = statement.getBody();
                if(body instanceof PsiBlockStatement){
                    final PsiCodeBlock block = ((PsiBlockStatement) body).getCodeBlock();
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
            replaceStatement(statement, newStatement.toString());
        }
    }


    public BaseInspectionVisitor buildVisitor(){
        return new ForeachStatementVisitor();
    }

    private static class ForeachStatementVisitor extends StatementInspectionVisitor{

        public void visitForeachStatement(@NotNull PsiForeachStatement statement){
            super.visitForeachStatement(statement);
            registerStatementError(statement);
        }
    }
}