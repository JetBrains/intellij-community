package com.siyeh.ig.security;

import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.ClassUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public class JDBCExecuteWithNonConstantStringInspection
        extends ExpressionInspection{
    /**
     * @noinspection StaticCollection
     */
    private static final Set<String> s_execMethodNames = new HashSet<String>(4);

    static {
        s_execMethodNames.add("execute");
        s_execMethodNames.add("executeQuery");
        s_execMethodNames.add("executeUpdate");
        s_execMethodNames.add("addBatch");
    }

    public String getDisplayName(){
        return "Call to 'Statement.execute()' or related method with non-constant string";
    }

    public String getGroupDisplayName(){
        return GroupNames.SECURITY_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "Call to Statement.#ref() with non-constant argument #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new RuntimeExecVisitor();
    }

    private static class RuntimeExecVisitor extends BaseInspectionVisitor{
        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiReferenceExpression methodExpression = expression
                    .getMethodExpression();
            if(methodExpression == null){
                return;
            }
            final String methodName = methodExpression.getReferenceName();
            if(!s_execMethodNames.contains(methodName)){
                return;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return;
            }
            final PsiClass aClass = method.getContainingClass();
            if(aClass == null){
                return;
            }
            if(!ClassUtils.isSubclass(aClass, "java.sql.Statement")){
                return;
            }
            final PsiExpressionList argumentList = expression.getArgumentList();
            if(argumentList == null){
                return;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if(args == null || args.length == 0){
                return;
            }
            final PsiExpression arg = args[0];
            final PsiType type = arg.getType();
            if(type == null){
                return;
            }
            final String typeText = type.getCanonicalText();
            if(!"java.lang.String".equals(typeText)){
                return;
            }
            final String stringValue =
                    (String) ConstantExpressionUtil.computeCastTo(arg, type);
            if(stringValue != null){
                return;
            }
            registerMethodCallError(expression);
        }
    }
}
