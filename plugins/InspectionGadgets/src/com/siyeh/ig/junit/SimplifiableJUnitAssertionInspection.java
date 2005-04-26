package com.siyeh.ig.junit;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.ClassUtils;

public class SimplifiableJUnitAssertionInspection extends ExpressionInspection{
    private final SimplifyJUnitAssertFix fix = new SimplifyJUnitAssertFix();

    public String getDisplayName(){
        return "Simplifiable JUnit assertion";
    }

    public String getGroupDisplayName(){
        return GroupNames.JUNIT_GROUP_NAME;
    }

    public String buildErrorString(PsiElement location){
        return "#ref() can be simplified #loc";
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class SimplifyJUnitAssertFix extends InspectionGadgetsFix{
        public String getName(){
            return "Simplify assertion";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(project, descriptor)){
                return;
            }
            final PsiElement methodNameIdentifier = descriptor.getPsiElement();
            final PsiMethodCallExpression callExpression =
                    (PsiMethodCallExpression) methodNameIdentifier.getParent()
                            .getParent();
            if(isAssertTrueThatCouldBeAssertEquality(callExpression)){
                replaceAssertTrueWithAssertEquals(callExpression, project);
            }
            else if(isAssertEqualsThatCouldBeAssertLiteral(callExpression)){
                replaceAssertEqualsWithAssertLiteral(callExpression, project);
            }
            else if(isAssertTrueThatCouldBeFail(callExpression)){
                replaceAssertWithFail(callExpression, project);
            }
            else if(isAssertFalseThatCouldBeFail(callExpression)){
                replaceAssertWithFail(callExpression, project);
            }
        }

        private void replaceAssertWithFail(PsiMethodCallExpression callExpression,
                                                       Project project){
            final PsiReferenceExpression methodExpression =
                    callExpression.getMethodExpression();

            final PsiMethod method = (PsiMethod) methodExpression.resolve();
            final PsiParameterList paramList = method.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();

            final PsiManager psiManager = callExpression.getManager();

            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            final PsiType stringType =
                    PsiType.getJavaLangString(psiManager, scope);
            final PsiType paramType1 = parameters[0].getType();
            final PsiExpressionList argumentList =
                    callExpression.getArgumentList();

            final PsiExpression[] args = argumentList.getExpressions();
            final PsiExpression message;
            if( parameters.length == 2){
                message = args[0];
            } else{
                message = null;
            }

            final StringBuffer newExpression =
                    new StringBuffer("fail(");
            if(message != null){
                newExpression.append(message.getText());
            }
            newExpression.append(')');
            replaceExpression(project, callExpression,
                              newExpression.toString());
        }

        private void replaceAssertTrueWithAssertEquals(PsiMethodCallExpression callExpression,
                                                       Project project){
            final PsiReferenceExpression methodExpression =
                    callExpression.getMethodExpression();

            final PsiMethod method = (PsiMethod) methodExpression.resolve();
            final PsiParameterList paramList = method.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();

            final PsiManager psiManager = callExpression.getManager();

            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            final PsiType stringType =
                    PsiType.getJavaLangString(psiManager, scope);
            final PsiType paramType1 = parameters[0].getType();
            final PsiExpressionList argumentList =
                    callExpression.getArgumentList();

            final PsiExpression[] args = argumentList.getExpressions();
            final int testPosition;
            final PsiExpression message;
            if(paramType1.equals(stringType) && parameters.length >= 2){
                testPosition = 1;
                message = args[0];
            } else{
                testPosition = 0;
                message = null;
            }
            final PsiExpression testArg = args[testPosition];

            PsiExpression lhs = null;
            PsiExpression rhs = null;
            if(testArg instanceof PsiBinaryExpression){
                lhs = ((PsiBinaryExpression) testArg).getLOperand();
                rhs = ((PsiBinaryExpression) testArg).getROperand();
            } else if(testArg instanceof PsiMethodCallExpression){
                final PsiMethodCallExpression call =
                        (PsiMethodCallExpression) testArg;
                final PsiReferenceExpression equalityMethodExpression =
                        call.getMethodExpression();
                final PsiExpressionList equalityArgumentList =
                        call.getArgumentList();
                final PsiExpression[] equalityArgs =
                        equalityArgumentList.getExpressions();
                rhs = equalityArgs[0];
                lhs = equalityMethodExpression.getQualifierExpression();
            }
            if(!(lhs instanceof PsiLiteralExpression) &&
                               rhs instanceof PsiLiteralExpression){
                final PsiExpression temp = lhs;
                lhs = rhs;
                rhs = temp;
            }
            final StringBuffer newExpression =
                    new StringBuffer("assertEquals(");
            if(message != null){
                newExpression.append(message.getText());
                newExpression.append(',');
            }
            newExpression.append(lhs.getText());
            newExpression.append(',');
            newExpression.append(rhs.getText());
            if(isFloatingPoint(lhs) || isFloatingPoint(rhs)){
                newExpression.append(",0.0");
            }
            newExpression.append(')');
            replaceExpression(project, callExpression,
                              newExpression.toString());
        }

        private void replaceAssertEqualsWithAssertLiteral(PsiMethodCallExpression callExpression,
                                                          Project project){
            final PsiReferenceExpression methodExpression =
                    callExpression.getMethodExpression();

            final PsiMethod method = (PsiMethod) methodExpression.resolve();
            final PsiParameterList paramList = method.getParameterList();
            final PsiParameter[] parameters = paramList.getParameters();

            final PsiManager psiManager = callExpression.getManager();

            final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
            final PsiType stringType =
                    PsiType.getJavaLangString(psiManager, scope);
            final PsiType paramType1 = parameters[0].getType();
            final PsiExpressionList argumentList =
                    callExpression.getArgumentList();

            final PsiExpression[] args = argumentList.getExpressions();
            final int firstTestPosition;
            final int secondTestPosition;
            final PsiExpression message;
            if(paramType1.equals(stringType) && parameters.length >= 3){
                firstTestPosition = 1;
                secondTestPosition = 2;
                message = args[0];
            } else{
                firstTestPosition = 0;
                secondTestPosition = 1;
                message = null;
            }
            final PsiExpression firstTestArg = args[firstTestPosition];
            final PsiExpression secondTestArg = args[secondTestPosition];
            final String literalValue;
            final String compareValue;
            if(isSimpleLiteral(firstTestArg)){
                literalValue = firstTestArg.getText();
                compareValue = secondTestArg.getText();
            } else{
                literalValue = secondTestArg.getText();
                compareValue = firstTestArg.getText();
            }
            final String uppercaseLiteralValue =
                    Character.toUpperCase(literalValue.charAt(0)) +
                    literalValue.substring(1);
            final StringBuffer newExpression =
                    new StringBuffer("assert" + uppercaseLiteralValue + '(');
            if(message != null){
                newExpression.append(message.getText());
                newExpression.append(',');
            }
            newExpression.append(compareValue);
            newExpression.append(')');
            replaceExpression(project, callExpression,
                              newExpression.toString());
        }

        private boolean isFloatingPoint(PsiExpression expression){
            final PsiType type = expression.getType();
            return PsiType.FLOAT.equals(type) || PsiType.DOUBLE.equals(type);
        }
    }

    public BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                               boolean onTheFly){
        return new SimplifiableJUnitAssertionVisitor(this,
                                                     inspectionManager,
                                                     onTheFly);
    }

    private static class SimplifiableJUnitAssertionVisitor
            extends BaseInspectionVisitor{
        private SimplifiableJUnitAssertionVisitor(BaseInspection inspection,
                                                  InspectionManager inspectionManager,
                                                  boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitMethodCallExpression(PsiMethodCallExpression expression){
            super.visitMethodCallExpression(expression);
            if(isAssertTrueThatCouldBeAssertEquality(expression)){
                registerMethodCallError(expression);
            } else if(isAssertEqualsThatCouldBeAssertLiteral(expression)){
                registerMethodCallError(expression);
            }else if(isAssertTrueThatCouldBeFail(expression)){
                registerMethodCallError(expression);
            }else if(isAssertFalseThatCouldBeFail(expression)){
                registerMethodCallError(expression);
            }
        }
    }

    private static boolean isAssertTrueThatCouldBeAssertEquality(PsiMethodCallExpression expression){
        if(!isAssertTrue(expression)){
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();

        final PsiMethod method = (PsiMethod) methodExpression.resolve();
        final PsiParameterList paramList = method.getParameterList();
        if(paramList == null){
            return false;
        }
        final PsiParameter[] parameters = paramList.getParameters();

        final PsiManager psiManager = expression.getManager();

        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiType stringType =
                PsiType.getJavaLangString(psiManager, scope);
        final PsiType paramType1 = parameters[0].getType();
        final int testPosition;
        if(paramType1.equals(stringType) && parameters.length > 1){
            testPosition = 1;
        } else{
            testPosition = 0;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        if(argumentList == null){
            return false;
        }
        final PsiExpression[] args = argumentList.getExpressions();
        final PsiExpression testArg = args[testPosition];
        if(testArg == null){
            return false;
        }
        return isEqualityComparison(testArg);
    }


    private static boolean isAssertTrueThatCouldBeFail(PsiMethodCallExpression expression){
        if(!isAssertTrue(expression)){
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();

        final PsiMethod method = (PsiMethod) methodExpression.resolve();
        final PsiParameterList paramList = method.getParameterList();
        if(paramList == null){
            return false;
        }
        final PsiParameter[] parameters = paramList.getParameters();

        final PsiManager psiManager = expression.getManager();

        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiType stringType =
                PsiType.getJavaLangString(psiManager, scope);
        final PsiType paramType1 = parameters[0].getType();
        final int testPosition;
        if(paramType1.equals(stringType) && parameters.length > 1){
            testPosition = 1;
        } else{
            testPosition = 0;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        if(argumentList == null){
            return false;
        }
        final PsiExpression[] args = argumentList.getExpressions();
        final PsiExpression testArg = args[testPosition];
        if(testArg == null){
            return false;
        }
        return testArg.getText().equals("false");
    }

    private static boolean isAssertFalseThatCouldBeFail(PsiMethodCallExpression expression){
        if(!isAssertFalse(expression)){
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();

        final PsiMethod method = (PsiMethod) methodExpression.resolve();
        final PsiParameterList paramList = method.getParameterList();
        if(paramList == null){
            return false;
        }
        final PsiParameter[] parameters = paramList.getParameters();

        final PsiManager psiManager = expression.getManager();

        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiType stringType =
                PsiType.getJavaLangString(psiManager, scope);
        final PsiType paramType1 = parameters[0].getType();
        final int testPosition;
        if(paramType1.equals(stringType) && parameters.length > 1){
            testPosition = 1;
        } else{
            testPosition = 0;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        if(argumentList == null){
            return false;
        }
        final PsiExpression[] args = argumentList.getExpressions();
        final PsiExpression testArg = args[testPosition];
        if(testArg == null){
            return false;
        }
        return testArg.getText().equals("true");
    }

    private static boolean isAssertEqualsThatCouldBeAssertLiteral(PsiMethodCallExpression expression){
        if(!isAssertEquals(expression)){
            return false;
        }
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();

        final PsiMethod method = (PsiMethod) methodExpression.resolve();
        final PsiParameterList paramList = method.getParameterList();
        if(paramList == null){
            return false;
        }
        final PsiParameter[] parameters = paramList.getParameters();

        final PsiManager psiManager = expression.getManager();

        final Project project = psiManager.getProject();
        final GlobalSearchScope scope = GlobalSearchScope.allScope(project);
        final PsiType stringType =
                PsiType.getJavaLangString(psiManager, scope);
        final PsiType paramType1 = parameters[0].getType();
        final int firstTestPosition;
        final int secondTestPosition;
        if(paramType1.equals(stringType) && parameters.length > 2){
            firstTestPosition = 1;
            secondTestPosition = 2;
        } else{
            firstTestPosition = 0;
            secondTestPosition = 1;
        }
        final PsiExpressionList argumentList = expression.getArgumentList();
        if(argumentList == null){
            return false;
        }
        final PsiExpression[] args = argumentList.getExpressions();
        final PsiExpression firstTestArg = args[firstTestPosition];
        final PsiExpression secondTestArg = args[secondTestPosition];
        if(firstTestArg == null){
            return false;
        }
        if(secondTestArg == null){
            return false;
        }
        return isSimpleLiteral(firstTestArg) || isSimpleLiteral(secondTestArg);
    }

    private static boolean isSimpleLiteral(PsiExpression arg){
        if(!(arg instanceof PsiLiteralExpression)){
            return false;
        }
        final String text = arg.getText();
        return "null".equals(text) || "true".equals(text) ||
                       "false".equals(text);
    }

    private static boolean isEqualityComparison(PsiExpression testArg){
        if(testArg instanceof PsiBinaryExpression){
            final PsiJavaToken sign =
                    ((PsiBinaryExpression) testArg).getOperationSign();
            if(sign == null){
                return false;
            }
            if(!sign.getTokenType().equals(JavaTokenType.EQEQ)){
                return false;
            }
            final PsiExpression lhs =
                    ((PsiBinaryExpression) testArg).getLOperand();
            if(lhs == null){
                return false;
            }
            final PsiExpression rhs =
                    ((PsiBinaryExpression) testArg).getROperand();
            if(rhs == null){
                return false;
            }
            final PsiType type = lhs.getType();
            if(type == null){
                return false;
            }
            return ClassUtils.isPrimitive(type);
        } else if(testArg instanceof PsiMethodCallExpression){
            final PsiMethodCallExpression call =
                    (PsiMethodCallExpression) testArg;
            final PsiReferenceExpression methodExpression =
                    call.getMethodExpression();
            final String methodName = methodExpression.getReferenceName();
            if(!"equals".equals(methodName)){
                return false;
            }
            final PsiExpressionList argumentList = call.getArgumentList();
            if(argumentList == null){
                return false;
            }
            final PsiExpression[] args = argumentList.getExpressions();
            if(args == null){
                return false;
            }
            if(args.length != 1){
                return false;
            }
            if(args[0] == null){
                return false;
            }
            return methodExpression.getQualifierExpression() != null;
        }
        return false;
    }

    private static boolean isAssertTrue(PsiMethodCallExpression expression){
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        if(!"assertTrue".equals(methodName)){
            return false;
        }
        final PsiMethod method = (PsiMethod) methodExpression.resolve();
        if(method == null){
            return false;
        }

        final PsiClass targetClass = method.getContainingClass();
        return targetClass != null &&
                       ClassUtils.isSubclass(targetClass,
                                             "junit.framework.Assert");
    }
    
    private static boolean isAssertFalse(PsiMethodCallExpression expression){
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        if(!"assertFalse".equals(methodName)){
            return false;
        }
        final PsiMethod method = (PsiMethod) methodExpression.resolve();
        if(method == null){
            return false;
        }

        final PsiClass targetClass = method.getContainingClass();
        return targetClass != null &&
                       ClassUtils.isSubclass(targetClass,
                                             "junit.framework.Assert");
    }

    private static boolean isAssertEquals(PsiMethodCallExpression expression){
        final PsiReferenceExpression methodExpression =
                expression.getMethodExpression();
        final String methodName = methodExpression.getReferenceName();
        if(!"assertEquals".equals(methodName)){
            return false;
        }
        final PsiMethod method = (PsiMethod) methodExpression.resolve();
        if(method == null){
            return false;
        }

        final PsiClass targetClass = method.getContainingClass();
        return targetClass != null &&
                       ClassUtils.isSubclass(targetClass,
                                             "junit.framework.Assert");
    }
}
