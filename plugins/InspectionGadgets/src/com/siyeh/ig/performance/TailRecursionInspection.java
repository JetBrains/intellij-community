package com.siyeh.ig.performance;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ControlFlowUtils;

public class TailRecursionInspection extends ExpressionInspection{
    public String getDisplayName(){
        return "Tail recursion";
    }

    public String getGroupDisplayName(){
        return GroupNames.PERFORMANCE_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Tail recursive call #ref() #loc";
    }

    protected InspectionGadgetsFix buildFix(PsiElement location){
        final PsiMethod containingMethod =
                (PsiMethod) PsiTreeUtil.getParentOfType(location,
                                                        PsiMethod.class);
        if(mayBeReplacedByIterativeMethod(containingMethod)){
            return new RemoveTailRecursionFix();
        } else{
            return null;
        }
    }

    private static boolean mayBeReplacedByIterativeMethod(PsiMethod containingMethod){
        if(!containingMethod.hasModifierProperty(PsiModifier.STATIC) &&
                !containingMethod.hasModifierProperty(PsiModifier.PRIVATE)){
            return false;
        }
        final PsiParameterList parameterList =
                containingMethod.getParameterList();
        final PsiParameter[] parameters = parameterList.getParameters();
        for(int i = 0; i < parameters.length; i++){
            final PsiParameter parameter = parameters[i];
            if(parameter.hasModifierProperty(PsiModifier.FINAL)){
                return false;
            }
        }
        return true;
    }

    private static class RemoveTailRecursionFix
            extends InspectionGadgetsFix{
        public String getName(){
            return "Replace tail recursion with iteration";
        }

        public void applyFix(Project project,
                             ProblemDescriptor descriptor){
            if (ReadonlyStatusHandler.getInstance(project).ensureFilesWritable(new VirtualFile[]{descriptor.getPsiElement().getContainingFile().getVirtualFile()}).hasReadonlyFiles()) return;
            try{
                final PsiElement methodNameToken =
                        descriptor.getPsiElement();
                final PsiMethod method =
                        (PsiMethod) PsiTreeUtil.getParentOfType(methodNameToken,
                                                                PsiMethod.class);

                final PsiCodeBlock body = method.getBody();
                final String replacementText;

                final PsiManager psiManager = PsiManager.getInstance(project);

                final CodeStyleManager codeStyleManager =
                        psiManager.getCodeStyleManager();

                final PsiElement[] children = body.getChildren();
                final StringBuffer buffer = new StringBuffer();
                final boolean[] containedTailCallInLoop = new boolean[1];
                containedTailCallInLoop[0] = false;
                for(int i = 1; i < children.length; i++){
                    replaceTailCalls(children[i], method, buffer, containedTailCallInLoop);
                }
                final String labelString;
                if(containedTailCallInLoop[0]){
                    labelString = method.getName() + ':';
                } else{
                    labelString = "";
                }
                replacementText = '{' + labelString + "while(true){" +
                        buffer + '}';

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


        private void replaceTailCalls(PsiElement element,
                              PsiMethod method,
                              StringBuffer out,
                              boolean[] containedTailCallInLoop){

            final String text = element.getText();
            if(isTailCallReturn(element, method)){
                final PsiReturnStatement returnStatement =
                        (PsiReturnStatement) element;
                final PsiMethodCallExpression call =
                        (PsiMethodCallExpression) returnStatement.getReturnValue();
                final PsiExpressionList argumentList = call.getArgumentList();
                final PsiExpression[] args =
                        argumentList.getExpressions();

                final PsiParameterList parameterList = method.getParameterList();
                final PsiParameter[] parameters =
                        parameterList.getParameters();
                final boolean isInBlock =
                        returnStatement.getParent() instanceof PsiCodeBlock;

                if(!isInBlock){
                    out.append('{');
                }
                for(int i = 0; i < parameters.length; i++){
                    final PsiParameter parameter = parameters[i];
                    final PsiExpression arg = args[i];
                    final String parameterName = parameter.getName();
                    final String argText = arg.getText();
                    out.append(parameterName + " = " + argText + ';');
                }
                if(ControlFlowUtils.blockCompletesWithStatement(method.getBody(), returnStatement))
                {
                     //don't do anything, as the continue is unnecessary
                }
                else if(ControlFlowUtils.isInLoop(element)){
                    final String methodName = method.getName();
                    containedTailCallInLoop[0] = true;
                    out.append("continue " + methodName + ';');
                } else{
                    out.append("continue;");
                }
                if(!isInBlock){
                    out.append('}');
                }
            } else{
                final PsiElement[] children = element.getChildren();
                if(children.length == 0){
                    out.append(text);
                } else{
                    for(int i = 0; i < children.length; i++){
                        final PsiElement child = children[i];
                        replaceTailCalls(child, method, out, containedTailCallInLoop);
                    }
                }
            }
        }

        private boolean isTailCallReturn(PsiElement element,
                                         PsiMethod containingMethod){
            if(!(element instanceof PsiReturnStatement)){
                return false;
            }
            final PsiReturnStatement returnStatement =
                    (PsiReturnStatement) element;
            final PsiExpression returnValue = returnStatement.getReturnValue();
            if(!(returnValue instanceof PsiMethodCallExpression)){
                return false;
            }
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression) returnValue;
            final PsiMethod method = call.resolveMethod();
            return containingMethod.equals(method);
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new TailRecursionVisitor(this, inspectionManager, onTheFly);
    }


    private static class TailRecursionVisitor extends BaseInspectionVisitor{
        private TailRecursionVisitor(BaseInspection inspection,
                                     InspectionManager inspectionManager,
                                     boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitReturnStatement(PsiReturnStatement statement){
            super.visitReturnStatement(statement);
            final PsiExpression returnValue = statement.getReturnValue();
            if(returnValue == null){
                return;
            }
            if(!(returnValue instanceof PsiMethodCallExpression)){
                return;
            }
            final PsiMethod containingMethod =
                    (PsiMethod) PsiTreeUtil.getParentOfType(statement,
                                                            PsiMethod.class);
            if(containingMethod == null){
                return;
            }
            final PsiMethodCallExpression returnCall =
                    (PsiMethodCallExpression) returnValue;
            final PsiMethod method = returnCall.resolveMethod();
            if(method == null){
                return;
            }
            if(!method.equals(containingMethod)){
                return;
            }

            final PsiReferenceExpression methodExpression =
                    returnCall.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            registerMethodCallError(returnCall);
        }
    }
}
