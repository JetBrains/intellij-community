package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.WellFormednessUtils;

public class AssignmentToMethodParameterInspection extends ExpressionInspection{
    private final AssignmentToMethodParameterFix fix =
            new AssignmentToMethodParameterFix();

    public String getDisplayName(){
        return "Assignment to method parameter";
    }

    public String getGroupDisplayName(){
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Assignment to method parameter #ref #loc ";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class AssignmentToMethodParameterFix
            extends InspectionGadgetsFix{
        public String getName(){
            return "Extract parameter as local variable";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            try{
                final PsiExpression variable =
                        (PsiExpression) descriptor.getPsiElement();
                final PsiMethod method =
                        (PsiMethod) PsiTreeUtil.getParentOfType(variable,
                                                                PsiMethod.class);

                final PsiCodeBlock body = method.getBody();
                final String replacementText;
                final PsiType type = variable.getType();

                final PsiManager psiManager = PsiManager.getInstance(project);

                final CodeStyleManager codeStyleManager =
                        psiManager.getCodeStyleManager();
                final String originalVariableName = variable.getText();
                final SuggestedNameInfo suggestions =
                        codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE,
                                                             originalVariableName + '1', variable, type);
                final String[] names = suggestions.names;
                final String baseName;
                if(names != null && names.length > 0){
                    baseName = names[0];
                } else{
                    baseName = "value";
                }
                final String variableName =
                        codeStyleManager.suggestUniqueVariableName(baseName,
                                                                   method,
                                                                   false);
                final String className = type.getPresentableText();
                final PsiElement[] children = body.getChildren();
                final StringBuffer buffer = new StringBuffer();
                for(int i = 1; i < children.length; i++){
                    replaceVariableName( children[i], variableName, originalVariableName, buffer);
                }
                replacementText = '{' + className + ' ' + variableName + " = " +
                        originalVariableName +
                        ';' +
                        buffer;

                final PsiElementFactory elementFactory =
                        psiManager.getElementFactory();
                final PsiCodeBlock block =
                        elementFactory.createCodeBlockFromText(replacementText,
                                                               null);
                body.replace(block);
                codeStyleManager.reformat(method);
            } catch(IncorrectOperationException e){
            }
        }

        private void replaceVariableName(PsiElement element,
                                         String newName,
                                         String originalName,
                                         StringBuffer out){

            final String text = element.getText();
            if(element instanceof PsiReferenceExpression){
                if(text.equals(originalName)){
                    out.append(newName);
                    return;
                }
            }
            final PsiElement[] children = element.getChildren();
            if(children.length == 0){
                out.append(text);
            } else{
                for(int i = 0; i < children.length; i++){
                    final PsiElement child = children[i];
                    replaceVariableName(child, newName,
                                        originalName, out);
                }
            }
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new AssignmentToMethodParameterVisitor(this, inspectionManager,
                                                      onTheFly);
    }

    private static class AssignmentToMethodParameterVisitor
            extends BaseInspectionVisitor{
        private AssignmentToMethodParameterVisitor(BaseInspection inspection,
                                                   InspectionManager inspectionManager,
                                                   boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitAssignmentExpression(PsiAssignmentExpression expression){
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            checkForMethodParam(lhs);
        }

        public void visitPrefixExpression(PsiPrefixExpression expression){
            super.visitPrefixExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            if(sign == null){
                return;
            }
            final IElementType tokenType = sign.getTokenType();
            if(!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                    !tokenType.equals(JavaTokenType.MINUSMINUS)){
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if(operand == null){
                return;
            }
            checkForMethodParam(operand);
        }

        public void visitPostfixExpression(PsiPostfixExpression expression){
            super.visitPostfixExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            if(sign == null){
                return;
            }
            final IElementType tokenType = sign.getTokenType();
            if(!tokenType.equals(JavaTokenType.PLUSPLUS) &&
                    !tokenType.equals(JavaTokenType.MINUSMINUS)){
                return;
            }
            final PsiExpression operand = expression.getOperand();
            if(operand == null){
                return;
            }
            checkForMethodParam(operand);
        }

        private void checkForMethodParam(PsiExpression expression){
            if(!(expression instanceof PsiReferenceExpression)){
                return;
            }
            final PsiReferenceExpression ref =
                    (PsiReferenceExpression) expression;
            final PsiElement variable = ref.resolve();
            if(!(variable instanceof PsiParameter)){
                return;
            }
            if(((PsiParameter) variable).getDeclarationScope() instanceof PsiCatchSection){
                return;
            }
            registerError(expression);
        }
    }
}
