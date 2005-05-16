package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

public class ArrayEqualsInspection extends ExpressionInspection{
    private InspectionGadgetsFix fix = new ArrayEqualsFix();

    public String getDisplayName(){
        return "'.equals()' called on array type";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return ".#ref() between arrays should probably be Arrays.equals() #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class ArrayEqualsFix extends InspectionGadgetsFix{
        public String getName(){
            return "replace with Arrays.equals";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(descriptor)) return;
            final PsiIdentifier name =
                    (PsiIdentifier) descriptor.getPsiElement();
            final PsiReferenceExpression expression =
                    (PsiReferenceExpression) name.getParent();
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression) expression.getParent();
            final PsiExpression qualifier = expression.getQualifierExpression();
            final String qualifierText = qualifier.getText();
            final PsiExpressionList argumentList = call.getArgumentList();
            assert argumentList != null;
            final PsiExpression[] args = argumentList.getExpressions();
            final String argText = args[0].getText();
            final String newExpressionText =
                    "java.util.Arrays.equals(" + qualifierText + ", " +
                    argText + ')';
            replaceExpressionAndShorten(project, call, newExpressionText);
        }

        private void replaceExpressionAndShorten(Project project,
                                                 PsiMethodCallExpression call,
                                                 String newExpressionText){
            try{
                final PsiManager manager = PsiManager.getInstance(project);
                final PsiElementFactory factory = manager.getElementFactory();
                final PsiExpression newExp =
                        factory.createExpressionFromText(newExpressionText,
                                                         null);
                final PsiElement replacementExp = call.replace(newExp);
                final CodeStyleManager styleManager =
                        manager.getCodeStyleManager();
                styleManager.shortenClassReferences(replacementExp);
                styleManager.reformat(replacementExp);
            } catch(IncorrectOperationException e){
                final Class aClass = getClass();
                final String className = aClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            }
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new ArrayEqualsVisitor(this, inspectionManager, onTheFly);
    }

    private static class ArrayEqualsVisitor extends BaseInspectionVisitor{
        private ArrayEqualsVisitor(BaseInspection inspection,
                                   InspectionManager inspectionManager,
                                   boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!"equals".equals(methodName)){
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if(argumentList == null){
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if(args == null){
                return;
            }
            if(args.length != 1){
                return;
            }
            final PsiExpression arg = args[0];
            if(arg == null){
                return;
            }
            final PsiType argType = arg.getType();
            if(!(argType instanceof PsiArrayType)){
                return;
            }
            final PsiExpression qualifier =
                    methodExpression.getQualifierExpression();
            if(qualifier == null){
                return;
            }
            final PsiType qualifierType = qualifier.getType();
            if(!(qualifierType instanceof PsiArrayType)){
                return;
            }
            registerMethodCallError(expression);
        }
    }
}