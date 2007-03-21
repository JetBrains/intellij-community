/*
 * Copyright 2003-2007 Dave Griffith, Bas Leijdekkers
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.siyeh.ig.bugs;

import com.intellij.psi.*;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.intellij.psi.util.PsiUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.psiutils.TypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.util.HashSet;
import java.util.Set;

public class MalformedXPathInspection extends BaseInspection {

    /** @noinspection StaticCollection*/
    @NonNls
    private static final Set<String> xpathMethodNames = new HashSet<String>(5);

    static{
      xpathMethodNames.add("compile");
      xpathMethodNames.add("evaluate");
    }

    @NotNull
    public String getDisplayName(){
        return InspectionGadgetsBundle.message(
                "malformed.xpath.expression.display.name");
    }

    @NotNull
    public String buildErrorString(Object... infos){
        return InspectionGadgetsBundle.message(
                "malformed.xpath.expression.problem.description");
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    public BaseInspectionVisitor buildVisitor(){
        return new MalformedXPathVisitor();
    }

    private static class MalformedXPathVisitor extends BaseInspectionVisitor{

        public void visitMethodCallExpression(
                @NotNull PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            final PsiExpressionList argList = expression.getArgumentList();
            final PsiExpression[] args = argList.getExpressions();
            if(args.length == 0){
                return;
            }
            final PsiExpression xpathArg = args[0];
            if(!TypeUtils.expressionHasType("java.lang.String", xpathArg)){
                return;
            }
            if(!PsiUtil.isConstantExpression(xpathArg)){
                return;
            }
            final PsiType regexType = xpathArg.getType();
            final String value =
                    (String) ConstantExpressionUtil.computeCastTo(xpathArg,
                            regexType);
            if(value == null){
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

        private static boolean callTakesRegex(
                PsiMethodCallExpression expression){
            final PsiReferenceExpression methodExpression =
                    expression.getMethodExpression();
            final String name = methodExpression.getReferenceName();
            if(!xpathMethodNames.contains(name)){
                return false;
            }
            final PsiMethod method = expression.resolveMethod();
            if(method == null){
                return false;
            }
            final PsiClass containingClass = method.getContainingClass();
            if(containingClass == null){
                return false;
            }
            final String className = containingClass.getQualifiedName();
            return "javax.xml.xpath.XPath".equals(className);
        }
    }
}