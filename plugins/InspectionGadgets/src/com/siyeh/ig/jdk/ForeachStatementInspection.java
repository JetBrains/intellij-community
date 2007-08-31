/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
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

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ForeachStatementInspection extends BaseInspection{

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "extended.for.statement.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "extended.for.statement.problem.descriptor");
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return new ForEachFix();
    }

    private static class ForEachFix extends InspectionGadgetsFix{

        @NotNull
        public String getName(){
            return InspectionGadgetsBundle.message(
                    "extended.for.statement.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiElement element = descriptor.getPsiElement();
            final PsiForeachStatement statement =
                    (PsiForeachStatement) element.getParent();

            final CodeStyleManager codeStyleManager =
                    CodeStyleManager.getInstance(project);
            assert statement != null;
            final PsiExpression iteratedValue = statement.getIteratedValue();
            final PsiParameter iterationParameter =
                    statement.getIterationParameter();
            final PsiType type = iterationParameter.getType();
            if (iteratedValue == null) {
                return;
            }
            @NonNls final StringBuffer newStatement = new StringBuffer();
            if(iteratedValue.getType() instanceof PsiArrayType){
                final String index =
                        codeStyleManager.suggestUniqueVariableName("i",
                                statement, true);
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
                newStatement.append(type .getPresentableText());
                newStatement.append(' ');
                newStatement.append(iterationParameter .getName());
                newStatement.append(" = ");
                newStatement.append(iteratedValue.getText());
                newStatement.append('[');
                newStatement.append(index);
                newStatement.append("];");
            } else{
                final String iterator =
                        codeStyleManager.suggestUniqueVariableName("it",
                                statement, true);
                final String typeText = type
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
                newStatement.append(iterationParameter.getName());
                newStatement.append(" = ");
                newStatement.append(iterator);
                newStatement.append(".next();");

            }
            final PsiStatement body = statement.getBody();
            if(body instanceof PsiBlockStatement){
                final PsiBlockStatement blockStatement =
                        (PsiBlockStatement)body;
                final PsiCodeBlock block = blockStatement.getCodeBlock();
                final PsiElement[] children = block.getChildren();
                for(int i = 1; i < children.length - 1; i++){
                    //skip the braces
                    newStatement.append(children[i].getText());
                }
            } else{
                final String bodyText;
                if (body == null) {
                    bodyText = "";
                } else {
                    bodyText = body.getText();
                }
                newStatement.append(bodyText);
            }
            newStatement.append('}');
            replaceStatement(statement, newStatement.toString());
        }
    }

    public BaseInspectionVisitor buildVisitor(){
        return new ForeachStatementVisitor();
    }

    private static class ForeachStatementVisitor
            extends BaseInspectionVisitor {

        public void visitForeachStatement(
                @NotNull PsiForeachStatement statement){
            super.visitForeachStatement(statement);
            registerStatementError(statement);
        }
    }
}