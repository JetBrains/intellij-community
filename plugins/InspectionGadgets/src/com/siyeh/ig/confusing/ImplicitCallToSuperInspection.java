package com.siyeh.ig.confusing;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.*;
import org.jetbrains.annotations.NotNull;

public class ImplicitCallToSuperInspection extends MethodInspection {
    private final AddExplicitSuperCall fix = new AddExplicitSuperCall();

    public String getDisplayName() {
        return "Implicit call to 'super()'";
    }

    public String getGroupDisplayName() {
        return GroupNames.CONFUSING_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location) {
        return "Implicit call to super() #ref #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class AddExplicitSuperCall extends InspectionGadgetsFix {
        public String getName() {
            return "Make construction of super() explicit";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor) {
            if(isQuickFixOnReadOnlyFile(descriptor)) return;
            try {
                final PsiElement methodName = descriptor.getPsiElement();
                final PsiMethod method = (PsiMethod) methodName.getParent();
                final PsiCodeBlock body = method.getBody();
                final PsiManager psiManager = PsiManager.getInstance(project);
                final PsiElementFactory factory = psiManager.getElementFactory();
                final PsiStatement newStatement = factory.createStatementFromText("super();", null);
                final CodeStyleManager styleManager = psiManager.getCodeStyleManager();
                final PsiJavaToken brace = body.getLBrace();
                body.addAfter(newStatement, brace);
                styleManager.reformat(body);
            } catch (IncorrectOperationException e) {
                final Class aClass = getClass();
                final String className = aClass.getName();
                final Logger logger = Logger.getInstance(className);
                logger.error(e);
            }
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager, boolean onTheFly) {
        return new ImplicitCallToSuperVisitor(this, inspectionManager, onTheFly);
    }

    private static class ImplicitCallToSuperVisitor extends BaseInspectionVisitor {
        private ImplicitCallToSuperVisitor(BaseInspection inspection, InspectionManager inspectionManager, boolean isOnTheFly) {
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethod(@NotNull PsiMethod method) {
            super.visitMethod(method);
            if (!method.isConstructor()) {
                return;
            }
            final PsiClass containingClass = method.getContainingClass();
            if (containingClass == null) {
                return;
            }
            if (containingClass.isEnum()) {
                return;
            }
            final PsiCodeBlock body = method.getBody();
            if (body == null) {
                return;
            }
            final PsiStatement[] statements = body.getStatements();
            if (statements == null) {
                return;
            }
            if (statements.length == 0) {
                registerMethodError(method);
                return;
            }
            final PsiStatement firstStatement = statements[0];
            if (isConstructorCall(firstStatement)) {
                return;
            }
            registerMethodError(method);
        }

        private static boolean isConstructorCall(PsiStatement statement) {
            if (!(statement instanceof PsiExpressionStatement)) {
                return false;
            }
            final PsiExpressionStatement expressionStatement = (PsiExpressionStatement) statement;
            final PsiExpression expression = expressionStatement.getExpression();
            if (expression == null) {
                return false;
            }
            if (!(expression instanceof PsiMethodCallExpression)) {
                return false;
            }
            final PsiMethodCallExpression methodCall = (PsiMethodCallExpression) expression;
            final PsiReferenceExpression methodExpression = methodCall.getMethodExpression();
            if (methodExpression == null) {
                return false;
            }
            final String text = methodExpression.getText();

            return "super".equals(text) || "this".equals(text);
        }
    }

}
