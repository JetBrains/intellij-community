package com.siyeh.ig.security;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class JDBCPrepareStatementWithNonConstantStringInspection extends ExpressionInspection{
    /** @noinspection StaticCollection*/
    private static Set<String> s_execMethodNames = new HashSet<String>(4);

    static
    {
         s_execMethodNames.add("prepareStatement");
         s_execMethodNames.add("prepareCall");
    }

    public String getDisplayName(){
        return "Call to 'Connection.prepareStatement()' or related method with non-constant string";
    }

    public String getGroupDisplayName(){
        return GroupNames.SECURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Call to Connection.#ref() with non-constant argument #loc";
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new RuntimeExecVisitor(this, inspectionManager, onTheFly);
    }

    private static class RuntimeExecVisitor extends BaseInspectionVisitor{
        private RuntimeExecVisitor(BaseInspection inspection,
                                   InspectionManager inspectionManager,
                                   boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if(methodExpression == null) {
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!s_execMethodNames.contains(methodName)) {
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null) {
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if(!ClassUtils.isSubclass(aClass, "java.sql.Connection")) {
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if(argumentList == null) {
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if(args == null || args.length == 0) {
                return;
            }
            final PsiExpression arg = args[0];
            final PsiType type = arg.getType();
            if(type == null) {
                return;
            }
            final String typeText = type.getCanonicalText();
            if(!"java.lang.String".equals(typeText)) {
                return;
            }
            final String stringValue =
                    (String) ConstantExpressionUtil.computeCastTo(arg, type);
            if(stringValue != null) {
                return;
            }
            registerMethodCallError(expression);
        }
    }
}
