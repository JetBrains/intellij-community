package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.WellFormednessUtils;
import org.jetbrains.annotations.NotNull;

public class AssignmentToCatchBlockParameterInspection
        extends ExpressionInspection{

    private final AssignmentToCatchBlockParameterFix fix =
            new AssignmentToCatchBlockParameterFix();

    public String getDisplayName(){
        return "Assignment to catch block parameter";
    }

    public String getGroupDisplayName(){
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Assignment to catch block parameter #ref #loc ";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class AssignmentToCatchBlockParameterFix
            extends InspectionGadgetsFix{
        public String getName(){
            return "Extract parameter as local variable";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(descriptor)) return;
            try{
                final PsiExpression variable =
                        (PsiExpression) descriptor.getPsiElement();
                final PsiCatchSection catchSection =
                        PsiTreeUtil.getParentOfType(variable,
                                                                      PsiCatchSection.class);

                final PsiCodeBlock body = catchSection.getCatchBlock();
                final String replacementText;
                final PsiType type = variable.getType();

                final PsiManager psiManager = PsiManager.getInstance(project);

                final CodeStyleManager codeStyleManager =
                        psiManager.getCodeStyleManager();
                final String originalVariableName = variable.getText();
                final SuggestedNameInfo suggestions =
                        codeStyleManager.suggestVariableName(VariableKind.LOCAL_VARIABLE,
                                                             originalVariableName +
                                                                     '1',
                                                             variable, type);
                final String[] names = suggestions.names;
                final String baseName;
                if(names != null && names.length > 0){
                    baseName = names[0];
                } else{
                    baseName = "value";
                }
                final String variableName =
                        codeStyleManager.suggestUniqueVariableName(baseName,
                                                                   catchSection,
                                                                   false);
                final String className = type.getPresentableText();
                final PsiElement[] children = body.getChildren();
                final StringBuffer buffer = new StringBuffer();
                for(int i = 1; i < children.length; i++){
                    replaceVariableName(children[i], variableName,
                                        originalVariableName, buffer);
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
                codeStyleManager.reformat(catchSection);
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
                for(final PsiElement child : children){
                    replaceVariableName(child, newName,
                                        originalName, out);
                }
            }
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new AssignmentToCatchBlockParameterVisitor(this,
                                                          inspectionManager,
                                                          onTheFly);
    }

    private static class AssignmentToCatchBlockParameterVisitor
            extends BaseInspectionVisitor{
        private AssignmentToCatchBlockParameterVisitor(BaseInspection inspection,
                                                       InspectionManager inspectionManager,
                                                       boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitAssignmentExpression(@NotNull PsiAssignmentExpression expression){
            super.visitAssignmentExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiExpression lhs = expression.getLExpression();
            if(!(lhs instanceof PsiReferenceExpression)){
                return;
            }
            final PsiReferenceExpression ref = (PsiReferenceExpression) lhs;
            final PsiElement variable = ref.resolve();
            if(!(variable instanceof PsiParameter)){
                return;
            }
            if(!(((PsiParameter) variable).getDeclarationScope() instanceof PsiCatchSection)){
                return;
            }
            registerError(lhs);
        }
    }
}
