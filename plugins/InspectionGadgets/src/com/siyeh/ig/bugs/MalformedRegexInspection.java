package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.Set;
import java.util.HashSet;

public class MalformedRegexInspection extends ExpressionInspection{
    /** @noinspection StaticCollection*/
    private static final Set regexMethodNames = new HashSet(5);

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

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new MalformedRegexVisitor(this, inspectionManager, onTheFly);
    }

    private static class MalformedRegexVisitor extends BaseInspectionVisitor{


        private MalformedRegexVisitor(BaseInspection inspection,
                                      InspectionManager inspectionManager,
                                      boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiExpressionList argList = expression.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            if(args.length == 0)
            {
                return;
            }

            final PsiExpression regexArg = args[0];
            if(regexArg == null){
                return;
            }
            final PsiType regexType = regexArg.getType();
            if(regexType == null)
            {
                return;
            }
            if(!callTakesRegex(expression)){
                return;
            }
            final String regexTypeText = regexType.getCanonicalText();
            if(!"java.lang.String".equals(regexTypeText)){
                return;
            }
            if(!PsiUtil.isConstantExpression(regexArg)){
                return;
            }
            final String value =
                    (String) ConstantExpressionUtil.computeCastTo(regexArg, regexType);
            if(value == null)
            {
                return;
            }
            //noinspection UnusedCatchParameter
            try{
                Pattern.compile(value);
            } catch(PatternSyntaxException e){
                registerError(regexArg);
            } catch(NullPointerException e){
                registerError(regexArg);
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
