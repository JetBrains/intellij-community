package com.siyeh.ig.serialization;

import com.siyeh.ig.ClassInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.SerializationUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class NonSerializableObjectBoundToHttpSessionInspection
        extends ClassInspection {

    public String getGroupDisplayName() {
        return GroupNames.SERIALIZATION_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "non.serializable.object.bound.to.http.session.problem.descriptor");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new NonSerializableObjectPassedToObjectStreamVisitor();
    }

    private static class NonSerializableObjectPassedToObjectStreamVisitor
            extends BaseInspectionVisitor {

        public void visitMethodCallExpression(PsiMethodCallExpression psiMethodCallExpression) {
            super.visitMethodCallExpression(psiMethodCallExpression);
            final PsiReferenceExpression methodExpression = psiMethodCallExpression.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if(!"putValue".equals(methodName) && "setAttribute".equals(methodName))
            {
                return;
            }
            final PsiExpressionList argList = psiMethodCallExpression.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            if(args.length!=2)
            {
                return;
            }
            final PsiMethod calledMethod = psiMethodCallExpression.resolveMethod();
            if(calledMethod == null)
            {
                return;
            }
            final PsiClass receiverClass = calledMethod.getContainingClass();
            if(receiverClass== null)
            {
                return;
            }
            if(!"javax.servlet.http.HttpSession".equals(receiverClass.getQualifiedName()))
            {
                return;
            }
            final PsiExpression arg = args[1];
            final PsiType argType = arg.getType();
            if(argType == null)
            {
                return;
            }
            if (SerializationUtils.isProbablySerializable(argType)) {
                return;
            }
            registerError(arg);
        }
    }
}
