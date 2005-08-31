/*
 * Copyright 2003-2005 Dave Griffith
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

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.HardcodedMethodConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;

public class ObjectEqualityInspection extends ExpressionInspection {
    /** @noinspection PublicField*/
    public boolean m_ignoreEnums = true;
    /** @noinspection PublicField*/
    public boolean m_ignoreClassObjects = false;

    private final EqualityToEqualsFix fix = new EqualityToEqualsFix();

    public String getDisplayName() {
        return InspectionGadgetsBundle.message("object.comparison.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    public JComponent createOptionsPanel() {
        final GridBagLayout layout = new GridBagLayout();
        final JPanel panel = new JPanel(layout);
        final JCheckBox arrayCheckBox = new JCheckBox(InspectionGadgetsBundle.message("object.comparison.enumerated.ignore.option"), m_ignoreEnums);
        final ButtonModel enumeratedObjectModel = arrayCheckBox.getModel();
        enumeratedObjectModel.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_ignoreEnums = enumeratedObjectModel.isSelected();
            }
        });
        final JCheckBox classObjectCheckbox = new JCheckBox(InspectionGadgetsBundle.message("object.comparison.klass.ignore.option"), m_ignoreClassObjects);
        final ButtonModel classObjectModel = classObjectCheckbox.getModel();
        classObjectModel.addChangeListener(new ChangeListener() {

            public void stateChanged(ChangeEvent e) {
                m_ignoreClassObjects = classObjectModel.isSelected();
            }
        });
        final GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        panel.add(arrayCheckBox, constraints);

        constraints.gridx = 0;
        constraints.gridy = 1;
        panel.add(classObjectCheckbox, constraints);
        return panel;
    }

    public String buildErrorString(PsiElement location) {
        return InspectionGadgetsBundle.message("object.comparison.problem.description");
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ObjectEqualityVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return fix;
    }

    private static class EqualityToEqualsFix extends InspectionGadgetsFix {
        public String getName() {
            return InspectionGadgetsBundle.message("object.comparison.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                                                                         throws IncorrectOperationException{
            final PsiElement comparisonToken = descriptor.getPsiElement();
            final PsiBinaryExpression
                    expression = (PsiBinaryExpression) comparisonToken.getParent();
            assert expression != null;
            boolean negated=false;
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (JavaTokenType.NE.equals(tokenType)) {
                negated = true;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression strippedLhs = ParenthesesUtils.stripParentheses(lhs);
            final PsiExpression rhs = expression.getROperand();
            if (rhs == null) {
                return;
            }
            final PsiExpression strippedRhs = ParenthesesUtils.stripParentheses(rhs);

            @NonNls final String expString;
            if (ParenthesesUtils.getPrecendence(strippedLhs) > ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
                expString = '(' + strippedLhs.getText() + ").equals(" + strippedRhs.getText() + ')';
            } else {
                expString = strippedLhs.getText() + ".equals(" + strippedRhs.getText() + ')';
            }
            final String newExpression;
            if (negated) {
                newExpression = '!' + expString;
            } else {
                newExpression = expString;
            }
            replaceExpression(expression, newExpression);
        }
    }

    private class ObjectEqualityVisitor extends BaseInspectionVisitor {


        public void visitBinaryExpression(@NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)){
                return;
            }
            if (!ComparisonUtils.isEqualityComparison(expression)) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            if (!isObjectType(rhs)) {
                return;
            }

            final PsiExpression lhs = expression.getLOperand();
            if (!isObjectType(lhs)) {
                return;
            }
            if (m_ignoreEnums && (isEnumType(rhs) || isEnumType(lhs))) {
                return;
            }
            if (m_ignoreClassObjects && (isClass(rhs) || isClass(lhs))) {
                return;
            }
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(expression,
                                                            PsiMethod.class);
            if(method != null) {
                final String methodName = method.getName();
                if (HardcodedMethodConstants.EQUALS.equals(methodName)) {
                    return;
                }
            }
            final PsiJavaToken sign = expression.getOperationSign();
            registerError(sign);
        }

        private boolean isClass(PsiExpression expression){
            if (expression == null) {
                return false;
            }
            if(expression instanceof PsiClassObjectAccessExpression)
            {
                return true;
            }
            final PsiType type = expression.getType();
            if(!(type instanceof PsiClassType))
            {
                return false;
            }
            final PsiClassType classType = (PsiClassType) type;
            final String className = classType.rawType().getCanonicalText();
            return "java.lang.Class".equals(className);
        }

        private boolean isEnumType(PsiExpression exp) {
            if (exp == null) {
                return false;
            }

            final PsiType type = exp.getType();
            if (type == null) {
                return false;
            }
            if(!(type instanceof PsiClassType))
            {
                return false;
            }
            final PsiClass aClass = ((PsiClassType)type).resolve();
            if(aClass == null)
            {
                return false;
            }
            return aClass.isEnum();
        }

        private  boolean isObjectType(PsiExpression exp) {
            if (exp == null) {
                return false;
            }

            final PsiType type = exp.getType();
            if (type == null) {
                return false;
            }
	        if (type instanceof PsiArrayType) {
	            return false;
            }
            return !(type instanceof PsiPrimitiveType)
                    && !TypeUtils.isJavaLangString(type);
        }

    }

}
