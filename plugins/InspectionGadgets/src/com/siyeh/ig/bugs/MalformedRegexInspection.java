package com.siyeh.ig.bugs;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class MalformedRegexInspection extends ExpressionInspection{
    /** @noinspection StaticCollection*/
    private static final Set<String> regexMethodNames = new HashSet<String>(5);

    static
    {
        regexMethodNames.add("compile");
        regexMethodNames.add("matches");
        regexMethodNames.add("replaceFirst");
        regexMethodNames.add("replaceAll");
        regexMethodNames.add("split");
    }

    public String getDisplayName(){
        return "Malformed regular expression";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return "Regular expression #ref is malformed #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MalformedRegexVisitor();
    }

    private static class MalformedRegexVisitor extends BaseInspectionVisitor{


        public void visitMethodCallExpression(@NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiExpressionList argList = expression.getArgumentList();
            if(argList == null){
                return;
            }
            final PsiExpression[] args = argList.getExpressions();
            if(args.length == 0)
            {
                return;
            }

            final PsiExpression regexArg = args[0];
            if(!TypeUtils.expressionHasType("java.lang.String", regexArg))
            {
                return;
            }
            if(!PsiUtil.isConstantExpression(regexArg)){
                return;
            }
            final PsiType regexType = regexArg.getType();
            final String value =
                    (String) ConstantExpressionUtil.computeCastTo(regexArg, regexType);
            if(value == null)
            {
                return;
            }
            if(!callTakesRegex(expression)){
                return;
            }
            //noinspection UnusedCatchParameter,ProhibitedExceptionCaught
            try{
                Pattern.compile(value);
            } catch(PatternSyntaxException e){
                registerError(regexArg);
            } catch(NullPointerException e){
                registerError(regexArg);     // due to a bug in the sun regex code
            }
        }

        private static boolean callTakesRegex(PsiMethodCallExpression expression){
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if(methodExpression == null)
            {
                return false;
            }
            final String name = methodExpression.getReferenceName();
            if(!regexMethodNames.contains(name))
            {
                return false;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null)
            {
                return false;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null)
            {
                return false;
            }
            final String className = containingClass.getQualifiedName();
            return "java.lang.String".equals(className) ||
                           "java.util.regex.Pattern".equals(className);
        }
    }
}
