package com.siyeh.ig.verbose;

import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.ConstantExpressionUtil;
import com.siyeh.ig.*;
import com.siyeh.ig.psiutils.WellFormednessUtils;

import java.util.Set;
import java.util.HashSet;

public class PointlessBooleanExpressionInspection extends ExpressionInspection{
    private final BooleanLiteralComparisonFix fix =
            new BooleanLiteralComparisonFix();

    public String getDisplayName(){
        return "Pointless boolean expression";
    }

    public String getGroupDisplayName(){
        return GroupNames.VERBOSE_GROUP_NAME;
    }

    public boolean isEnabledByDefault(){
        return true;
    }

    protected BaseInspectionVisitor createVisitor(InspectionManager inspectionManager,
                                                  boolean onTheFly){
        return new PointlessBooleanExpressionVisitor(this, inspectionManager,
                                                     onTheFly);
    }

    public String buildErrorString(PsiElement location){
        if(location instanceof PsiBinaryExpression){
            return "#ref can be simplified to " +
                           calculateSimplifiedBinaryExpression((PsiBinaryExpression) location) +
                           " #loc";
        } else{
            return "#ref can be simplified to " +
                           calculateSimplifiedPrefixExpression((PsiPrefixExpression) location) +
                           " #loc";
        }
    }

    private static String calculateSimplifiedBinaryExpression(PsiBinaryExpression expression){
        final PsiJavaToken sign = expression.getOperationSign();
        final PsiExpression lhs = expression.getLOperand();

        final PsiExpression rhs = expression.getROperand();
        if(rhs == null){
            return null;
        }
        final IElementType tokenType = sign.getTokenType();
        final String rhsText = rhs.getText();
        final String lhsText = lhs.getText();
        if(tokenType.equals(JavaTokenType.ANDAND) ||
                   tokenType.equals(JavaTokenType.AND)){
            if(isTrue(lhs)){
                return rhsText;
            } else{
                return lhsText;
            }
        } else if(tokenType.equals(JavaTokenType.OROR) ||
                          tokenType.equals(JavaTokenType.OR)){
            if(isFalse(lhs)){
                return rhsText;
            } else{
                return lhsText;
            }
        } else if(tokenType.equals(JavaTokenType.XOR) ||
                          tokenType.equals(JavaTokenType.NE)){
            if(isFalse(lhs)){
                return rhsText;
            } else if(isFalse(rhs)){
                return lhsText;
            } else if(isTrue(lhs)){
                return '!' + rhsText;
            } else{
                return '!' + lhsText;
            }
        } else if(tokenType.equals(JavaTokenType.EQEQ)){
            if(isTrue(lhs)){
                return rhsText;
            } else if(isTrue(rhs)){
                return lhsText;
            } else if(isFalse(lhs)){
                return '!' + rhsText;
            } else{
                return '!' + lhsText;
            }
        } else{
            return "";
        }
    }

    private static String calculateSimplifiedPrefixExpression(PsiPrefixExpression expression){
        final PsiExpression operand = expression.getOperand();
        if(isTrue(operand)){
            return "false";
        } else{
            return "true";
        }
    }

    public InspectionGadgetsFix buildFix(PsiElement location){
        return fix;
    }

    private static class BooleanLiteralComparisonFix
            extends InspectionGadgetsFix{
        public String getName(){
            return "Simplify";
        }

        public void applyFix(Project project, ProblemDescriptor descriptor){
            if(isQuickFixOnReadOnlyFile(project, descriptor)) return;
            final PsiElement element = descriptor.getPsiElement();
            if(element instanceof PsiBinaryExpression){
                final PsiBinaryExpression expression =
                        (PsiBinaryExpression) element;
                final String replacementString =
                        calculateSimplifiedBinaryExpression(expression);
                replaceExpression(project, expression, replacementString);
            } else{
                final PsiPrefixExpression expression =
                        (PsiPrefixExpression) element;
                final String replacementString =
                        calculateSimplifiedPrefixExpression(expression);
                replaceExpression(project, expression, replacementString);
            }
        }
    }

    private static class PointlessBooleanExpressionVisitor
            extends BaseInspectionVisitor{
        private static final Set<IElementType> booleanTokens = new HashSet<IElementType>(10);

        static
        {
            booleanTokens.add(JavaTokenType.ANDAND);
            booleanTokens.add(JavaTokenType.AND);
            booleanTokens.add(JavaTokenType.OROR);
            booleanTokens.add(JavaTokenType.OR);
            booleanTokens.add(JavaTokenType.XOR);
            booleanTokens.add(JavaTokenType.EQEQ);
            booleanTokens.add(JavaTokenType.NE);
        }
        private PointlessBooleanExpressionVisitor(BaseInspection inspection,
                                                  InspectionManager inspectionManager,
                                                  boolean isOnTheFly){
            super(inspection, inspectionManager, isOnTheFly);
        }

        public void visitClass(PsiClass aClass){
            //to avoid drilldown
        }

        public void visitBinaryExpression(PsiBinaryExpression expression){
            super.visitBinaryExpression(expression);
            if(!WellFormednessUtils.isWellFormed(expression)){
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if(!booleanTokens.contains(tokenType))
            {
                return;
            }

            final PsiExpression rhs = expression.getROperand();
            final PsiType rhsType = rhs.getType();
            if(rhsType == null){
                return;
            }
            if(!rhsType.equals(PsiType.BOOLEAN)&&
                       !rhsType.equalsToText("java.lang.Boolean")){
                return;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiType lhsType = lhs.getType();
            if(lhsType == null){
                return;
            }
            if(!lhsType.equals(PsiType.BOOLEAN) &&
                       !lhsType.equalsToText("java.lang.Boolean")){
                return;
            }
            final boolean isPointless;
            if(tokenType.equals(JavaTokenType.EQEQ) ||
                       tokenType.equals(JavaTokenType.NE)){
                isPointless = equalityExpressionIsPointless(lhs, rhs);
            } else if(tokenType.equals(JavaTokenType.ANDAND) ||
                              tokenType.equals(JavaTokenType.AND)){
                isPointless = andExpressionIsPointless(lhs, rhs);
            } else if(tokenType.equals(JavaTokenType.OROR) ||
                              tokenType.equals(JavaTokenType.OR)){
                isPointless = orExpressionIsPointless(lhs, rhs);
            } else if(tokenType.equals(JavaTokenType.XOR)){
                isPointless = xorExpressionIsPointless(lhs, rhs);
            } else{
                isPointless = false;
            }
            if(!isPointless){
                return;
            }
            registerError(expression);
        }

        public void visitPrefixExpression(PsiPrefixExpression expression){
            super.visitPrefixExpression(expression);
            final PsiJavaToken sign = expression.getOperationSign();
            if(sign == null){
                return;
            }
            final PsiExpression operand = expression.getOperand();
            final IElementType tokenType = sign.getTokenType();
            if(!(!tokenType.equals(JavaTokenType.EXCL) ||
                    !notExpressionIsPointless(operand))){
                registerError(expression);
            }
        }
    }

    private static boolean equalityExpressionIsPointless(PsiExpression lhs,
                                                         PsiExpression rhs){
        return isTrue(lhs) || isTrue(rhs) || isFalse(lhs) || isFalse(rhs);
    }

    private static boolean andExpressionIsPointless(PsiExpression lhs,
                                                    PsiExpression rhs){
        return isTrue(lhs) || isTrue(rhs);
    }

    private static boolean orExpressionIsPointless(PsiExpression lhs,
                                                   PsiExpression rhs){
        return isFalse(lhs) || isFalse(rhs);
    }

    private static boolean xorExpressionIsPointless(PsiExpression lhs,
                                                    PsiExpression rhs){
        return isTrue(lhs) || isTrue(rhs) || isFalse(lhs) || isFalse(rhs);
    }

    private static boolean notExpressionIsPointless(PsiExpression arg){
        return isFalse(arg) || isTrue(arg);
    }

    private static boolean isTrue(PsiExpression expression){
        if(expression == null){
            return false;
        }
        final Boolean value =
                (Boolean) ConstantExpressionUtil.computeCastTo(expression,
                                                               PsiType.BOOLEAN);
        return value != null && (value);
    }

    private static boolean isFalse(PsiExpression expression){
        if(expression == null){
            return false;
        }
        final Boolean value =
                (Boolean) ConstantExpressionUtil.computeCastTo(expression,
                                                               PsiType.BOOLEAN);
        return value != null && !(value);
    }
}
