/*
 * Copyright 2003-2006 Dave Griffith, Bas Leijdekkers
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
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.*;
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiElementProcessor;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.ExpressionInspection;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.ComparisonUtils;
import com.siyeh.ig.psiutils.MethodUtils;
import com.siyeh.ig.psiutils.ParenthesesUtils;
import com.siyeh.ig.psiutils.TypeUtils;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.Collection;

public class ObjectEqualityInspection extends ExpressionInspection {

    /** @noinspection PublicField*/
    public boolean m_ignoreEnums = true;

    /** @noinspection PublicField*/
    public boolean m_ignoreClassObjects = false;

    /** @noinspection PublicField*/
    public boolean m_ignorePrivateConstructors = false;

    public String getDisplayName() {
        return InspectionGadgetsBundle.message(
                "object.comparison.display.name");
    }

    public String getGroupDisplayName() {
        return GroupNames.BUGS_GROUP_NAME;
    }

    @NotNull
    public String buildErrorString(Object... infos) {
        return InspectionGadgetsBundle.message(
                "object.comparison.problem.description");
    }

    public JComponent createOptionsPanel() {
        final MultipleCheckboxOptionsPanel optionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "object.comparison.enumerated.ignore.option"), "m_ignoreEnums");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "object.comparison.klass.ignore.option"),
                "m_ignoreClassObjects");
        optionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "object.equality.ignore.between.objects.of.a.type.with.only.private.constructors.option"),
                "m_ignorePrivateConstructors");
        return optionsPanel;
    }

    public BaseInspectionVisitor buildVisitor() {
        return new ObjectEqualityVisitor();
    }

    public InspectionGadgetsFix buildFix(PsiElement location) {
        return new EqualityToEqualsFix();
    }

    private static class EqualityToEqualsFix extends InspectionGadgetsFix {

        public String getName() {
            return InspectionGadgetsBundle.message(
                    "object.comparison.replace.quickfix");
        }

        public void doFix(Project project, ProblemDescriptor descriptor)
                throws IncorrectOperationException {
            final PsiElement comparisonToken = descriptor.getPsiElement();
            final PsiBinaryExpression expression = (PsiBinaryExpression)
                    comparisonToken.getParent();
            if (expression == null) {
                return;
            }
            boolean negated=false;
            final PsiJavaToken sign = expression.getOperationSign();
            final IElementType tokenType = sign.getTokenType();
            if (JavaTokenType.NE.equals(tokenType)) {
                negated = true;
            }
            final PsiExpression lhs = expression.getLOperand();
            final PsiExpression strippedLhs =
                    ParenthesesUtils.stripParentheses(lhs);
            if (strippedLhs == null) {
                return;
            }
            final PsiExpression rhs = expression.getROperand();
            final PsiExpression strippedRhs =
                    ParenthesesUtils.stripParentheses(rhs);
            if (strippedRhs == null) {
                return;
            }
            @NonNls final String expString;
            if (ParenthesesUtils.getPrecedence(strippedLhs) >
                    ParenthesesUtils.METHOD_CALL_PRECEDENCE) {
                expString = '(' + strippedLhs.getText() + ").equals(" +
                        strippedRhs.getText() + ')';
            } else {
                expString = strippedLhs.getText() + ".equals(" +
                        strippedRhs.getText() + ')';
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

        public void visitBinaryExpression(
                @NotNull PsiBinaryExpression expression) {
            super.visitBinaryExpression(expression);
            if(!(expression.getROperand() != null)) {
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
            if (m_ignorePrivateConstructors &&
                    (typeHasPrivateConstructor(lhs) ||
                    typeHasPrivateConstructor(rhs))) {
                return;
            }
            final PsiMethod method =
                    PsiTreeUtil.getParentOfType(expression,
                            PsiMethod.class);
            if (method != null && MethodUtils.isEquals(method)) {
                return;
            }
            final PsiJavaToken sign = expression.getOperationSign();
            registerError(sign);
        }

        private boolean typeHasPrivateConstructor(
                @Nullable PsiExpression expression) {
            if (expression == null) {
                return false;
            }
            final PsiType type = expression.getType();
            if (!(type instanceof PsiClassType)) {
                return false;
            }
            final PsiClassType classType = (PsiClassType)type;
            final PsiClass aClass = classType.resolve();
            if (aClass != null && aClass.isInterface()) {
                return implementorsHaveOnlyPrivateConstructors(aClass);
            } else {
                return hasOnlyPrivateConstructors(aClass);
            }
        }

        private boolean implementorsHaveOnlyPrivateConstructors(
                final PsiClass aClass) {
            final PsiManager manager = aClass.getManager();
            final PsiSearchHelper searchHelper = manager.getSearchHelper();
            final GlobalSearchScope scope =
                    GlobalSearchScope.allScope(aClass.getProject());
            final PsiElementProcessor.CollectElementsWithLimit<PsiClass>
                    processor =
                    new PsiElementProcessor.CollectElementsWithLimit(6);
            final ProgressManager progressManager = ProgressManager.getInstance();
            progressManager.runProcess(new Runnable() {
                public void run() {
                    searchHelper.processInheritors(processor, aClass, scope,
                            true, true);
                }
            }, null);
            if (processor.isOverflow()) {
                return false;
            }
            final Collection<PsiClass> implementors = processor.getCollection();
            for (PsiClass implementor : implementors) {
                if (!implementor.isInterface() &&
                        !implementor.hasModifierProperty(
                                PsiModifier.ABSTRACT)) {
                    if (!hasOnlyPrivateConstructors(implementor)) {
                        return false;
                    }
                }
            }
            return true;
        }

        private boolean hasOnlyPrivateConstructors(PsiClass aClass) {
            if (aClass == null) {
                return false;
            }
            final PsiMethod[] constructors = aClass.getConstructors();
            if (constructors.length == 0) {
                return false;
            }
            for (PsiMethod constructor : constructors) {
                if (!constructor.hasModifierProperty(PsiModifier.PRIVATE)) {
                    return false;
                }
            }
            return true;
        }

        private boolean isClass(@Nullable PsiExpression expression) {
            if (expression == null) {
                return false;
            }
            if (expression instanceof PsiClassObjectAccessExpression) {
                return true;
            }
            final PsiType type = expression.getType();
            if (!(type instanceof PsiClassType)) {
                return false;
            }
            final PsiClassType classType = (PsiClassType) type;
            final PsiClassType rawType = classType.rawType();
            final String className = rawType.getCanonicalText();
            return "java.lang.Class".equals(className);
        }

        private boolean isEnumType(@Nullable PsiExpression expression) {
            if (expression == null) {
                return false;
            }
            final PsiType type = expression.getType();
            if (type == null) {
                return false;
            }
            if(!(type instanceof PsiClassType)) {
                return false;
            }
            final PsiClass aClass = ((PsiClassType)type).resolve();
            if(aClass == null) {
                return false;
            }
            return aClass.isEnum();
        }

        private  boolean isObjectType(PsiExpression expression) {
            if (expression == null) {
                return false;
            }
            final PsiType type = expression.getType();
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