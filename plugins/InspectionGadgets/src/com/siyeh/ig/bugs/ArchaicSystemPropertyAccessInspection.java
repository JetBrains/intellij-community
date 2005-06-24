package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ArchaicSystemPropertyAccessInspection extends ExpressionInspection {
    public String getID(){
        return "UseOfArchaicSystemPropertyAccessors";
    }
    public String getDisplayName() {
        return "Use of archaic system property accessors";
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    @Nullable
    protected InspectionGadgetsFix[] buildFixes(PsiElement location){
        return new InspectionGadgetsFix[]{ new ReplaceWithParseMethodFix(), new ReplaceWithStandardPropertyAccessFix()};
    }

    public String buildErrorString(PsiElement location) {

        final PsiElement parent = location.getParent();
        assert parent != null;
        final PsiMethodCallExpression call =
                (PsiMethodCallExpression) parent.getParent();
        if(isIntegerGetInteger(call)){
            return "Call to Integer.#ref() accesses system properties, perhaps confusingly #loc";
        } else if(isLongGetLong(call)){
            return "Call to Long.#ref() accesses system properties, perhaps confusingly #loc";
        } else{
            return "Call to Boolean.#ref()accesses system properties, perhaps confusingly #loc";
        }
    }

    private static class ReplaceWithParseMethodFix extends InspectionGadgetsFix{
        public String getName(){
            return "Replace with parse method";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiIdentifier location =
                    (PsiIdentifier) descriptor.getPsiElement();
            final PsiElement parent = location.getParent();
            assert parent != null;
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression) parent.getParent();
            assert call != null;
            final PsiExpressionList argList = call.getArgumentList();
            assert argList != null;
            final PsiExpression[] args = argList.getExpressions();
            final String argText = args[0].getText();
            final String parseMethodCall;
            if(isIntegerGetInteger(call)){
                parseMethodCall = "Integer.valueOf("+ argText + ')';
            } else if(isLongGetLong(call)){
                parseMethodCall = "Long.valueOf(" + argText + ')';
            } else{
                parseMethodCall = "Boolean.valueOf(" + argText + ')';
            }
            replaceExpression( call, parseMethodCall);
        }
    }

    private static class ReplaceWithStandardPropertyAccessFix extends InspectionGadgetsFix{
        public String getName(){
            return "Replace with standard property access";
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException{
            final PsiIdentifier location =
                    (PsiIdentifier) descriptor.getPsiElement();
            final PsiElement parent = location.getParent();
            assert parent != null;
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression) parent.getParent();
            assert call != null;
            final PsiExpressionList argList = call.getArgumentList();
            assert argList != null;
            final PsiExpression[] args = argList.getExpressions();
            final String argText = args[0].getText();
            final String parseMethodCall;
            if(isIntegerGetInteger(call)){
                parseMethodCall = "Integer.parseInt(System.getProperty("+ argText + "))";
            } else if(isLongGetLong(call)){
                parseMethodCall = "Long.parseLong(System.getProperty("
                        + argText + "))";
            } else{
                parseMethodCall = "Boolean.parseBoolean(System.getProperty("
                        + argText + "))";
            }
            replaceExpression( call, parseMethodCall);
        }
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ArchaicSystemPropertyAccessVisitor();
    }

    private static class ArchaicSystemPropertyAccessVisitor extends BaseInspectionVisitor {

        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression) {
            super.visitMethodCallExpression(expression);

            if(isIntegerGetInteger(expression) ||
                    isLongGetLong(expression)||
                    isBooleanGetBoolean(expression)){
                registerMethodCallError(expression);
            }

        }
    }

    private static boolean isIntegerGetInteger(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if(methodExpression == null){
                return false;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"getInteger".equals(methodName) ) {
                return false;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return false;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return false;
            }
            final String className = aClass.getQualifiedName();
            if (className == null) {
                return false;
            }
            return "java.lang.Integer".equals(className);
        }
    private static boolean isLongGetLong(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if(methodExpression == null){
                return false;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"getInteger".equals(methodName) ) {
                return false;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return false;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return false;
            }
            final String className = aClass.getQualifiedName();
            if (className == null) {
                return false;
            }
            return "java.lang.Integer".equals(className);
        }

        private static boolean isBooleanGetBoolean(PsiMethodCallExpression expression) {
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if(methodExpression == null){
                return false;
            }
            final String methodName = methodExpression.getReferenceName();
            if (!"getBoolean".equals(methodName) ) {
                return false;
            }
            final PsiMethod method = expression.resolveMethod();
            if (method == null) {
                return false;
            }
            final PsiClass aClass = method.getContainingClass();
            if (aClass == null) {
                return false;
            }
            final String className = aClass.getQualifiedName();
            if (className == null) {
                return false;
            }
            return "java.lang.Boolean".equals(className);
        }

}
