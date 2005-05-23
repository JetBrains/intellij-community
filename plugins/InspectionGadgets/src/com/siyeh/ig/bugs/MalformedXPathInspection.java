package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.GroupNames;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NotNull;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.HashSet;
import java.util.Set;

public class MalformedXPathInspection extends ExpressionInspection{
    /** @noinspection StaticCollection*/
    private static final Set<String> xpathMethodNames = new HashSet<String>(5);

    static
    {
        xpathMethodNames.add("compile");
        xpathMethodNames.add("evaluate");
    }

    public String getDisplayName(){
        return "Malformed XPath expression";
    }

    public String getGroupDisplayName(){
        return GroupNames.BUGS_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public String buildErrorString(PsiElement location){
        return "XPath expression #ref is malformed #loc";
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MalformedXPathVisitor();
    }

    private static class MalformedXPathVisitor extends BaseInspectionVisitor{


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

            final PsiExpression xpathArg = args[0];
            if(!TypeUtils.expressionHasType("java.lang.String", xpathArg))
            {
                return;
            }
            if(!PsiUtil.isConstantExpression(xpathArg)){
                return;
            }
            final PsiType regexType = xpathArg.getType();
            final String value =
                    (String) ConstantExpressionUtil.computeCastTo(xpathArg, regexType);
            if(value == null)
            {
                return;
            }
            if(!callTakesRegex(expression)){
                return;
            }
            final XPathFactory xpathFactory = XPathFactory.newInstance();
            final XPath xpath = xpathFactory.newXPath();
            //noinspection UnusedCatchParameter,ProhibitedExceptionCaught
            try{
                xpath.compile(value);
            }catch(XPathExpressionException ignore){
                registerError(xpathArg);
            }
        }

        private static boolean callTakesRegex(PsiMethodCallExpression expression){
            final PsiReferenceExpression methodExpression = expression.getMethodExpression();
            if(methodExpression == null)
            {
                return false;
            }
            final String name = methodExpression.getReferenceName();
            if(!xpathMethodNames.contains(name))
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
            return "javax.xml.xpath.XPath".equals(className);
        }
    }
}
