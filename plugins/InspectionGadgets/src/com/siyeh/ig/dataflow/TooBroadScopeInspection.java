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
package com.siyeh.ig.dataflow;

import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.psiutils.HighlightUtil;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.Collection;

public class TooBroadScopeInspection extends BaseInspection
{

    /** @noinspection PublicField for externalization*/
    public boolean m_allowConstructorAsInitializer = false;

    /** @noinspection PublicField for externalization*/
    public boolean m_onlyLookAtBlocks = false;

    @NotNull
    public String getDisplayName()
    {
        return InspectionGadgetsBundle.message("too.broad.scope.display.name");
    }

    @NotNull
    public String getID()
    {
        return "TooBroadScope";
    }

    @Nullable
    public JComponent createOptionsPanel()
    {
        // html allows text to wrap
        final MultipleCheckboxOptionsPanel checkboxOptionsPanel =
                new MultipleCheckboxOptionsPanel(this);
        checkboxOptionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "too.broad.scope.only.blocks.option"),
                "m_onlyLookAtBlocks");
        checkboxOptionsPanel.addCheckbox(InspectionGadgetsBundle.message(
                "too.broad.scope.allow.option"),
                "m_allowConstructorAsInitializer");
        return checkboxOptionsPanel;
    }

    @NotNull
    protected String buildErrorString(Object... infos)
    {
        return InspectionGadgetsBundle.message(
                "too.broad.scope.problem.descriptor");
    }

    public InspectionGadgetsFix buildFix(PsiElement location)
    {
        return new TooBroadScopeInspectionFix(location.getText());
    }

    private class TooBroadScopeInspectionFix extends InspectionGadgetsFix
    {
        private String m_name;

        TooBroadScopeInspectionFix(String name)
        {
            m_name = name;
        }

        @NotNull
        public String getName()
        {
            return InspectionGadgetsBundle.message(
                    "too.broad.scope.narrow.quickfix", m_name);
        }

        protected void doFix(@NotNull Project project,
                             ProblemDescriptor descriptor)
                throws IncorrectOperationException
        {
            final PsiElement variableIdentifier =
                    descriptor.getPsiElement();
            final PsiVariable variable =
                    (PsiVariable)variableIdentifier.getParent();
            assert variable != null;
            final Query<PsiReference> query =
                    ReferencesSearch.search(variable, variable.getUseScope());
            final Collection<PsiReference> referenceCollection =
                    query.findAll();
	        final PsiElement[] referenceElements =
                    new PsiElement[referenceCollection.size()];
	        int index = 0;
	        for (PsiReference reference : referenceCollection)
	        {
		        final PsiElement referenceElement = reference.getElement();
		        referenceElements[index] = referenceElement;
		        index++;
	        }
            PsiElement commonParent =
                    ScopeUtils.getCommonParent(referenceElements);
            assert commonParent != null;
            final PsiExpression initializer = variable.getInitializer();
            if (initializer != null)
            {
                final PsiElement variableScope =
                        PsiTreeUtil.getParentOfType(variable,
                                PsiCodeBlock.class, PsiForStatement.class);
                assert variableScope != null;
                commonParent = ScopeUtils.moveOutOfLoops(
                        commonParent, variableScope);
                if (commonParent == null)
                {
                    return;
                }
            }
            final PsiElement referenceElement = referenceElements[0];
            final PsiElement firstReferenceScope =
                    PsiTreeUtil.getParentOfType(referenceElement,
                            PsiCodeBlock.class, PsiForStatement.class);
            assert firstReferenceScope != null;
            PsiDeclarationStatement newDeclaration;
            if (firstReferenceScope.equals(commonParent))
            {
                newDeclaration = moveDeclarationToLocation(variable,
                        referenceElement);
            }
            else
            {
                final PsiElement commonParentChild =
                        ScopeUtils.getChildWhichContainsElement(
                                commonParent, referenceElement);
                assert commonParentChild != null;
                final PsiElement location = commonParentChild.getPrevSibling();
                newDeclaration = createNewDeclaration(variable, initializer);
                newDeclaration = (PsiDeclarationStatement)
                        commonParent.addAfter(newDeclaration, location);
            }
            final CodeStyleManager codeStyleManager =
                    CodeStyleManager.getInstance(project);
            newDeclaration = (PsiDeclarationStatement)
                    codeStyleManager.reformat(newDeclaration);
            removeOldVariable(variable);
            if (isOnTheFly())
            {
                HighlightUtil.highlightElement(newDeclaration);
            }
        }

        private void removeOldVariable(@NotNull PsiVariable variable)
                throws IncorrectOperationException
        {
            final PsiDeclarationStatement declaration =
                    (PsiDeclarationStatement)variable.getParent();
            assert declaration != null;
            final PsiElement[] declaredElements =
                    declaration.getDeclaredElements();
            if (declaredElements.length == 1)
            {
                declaration.delete();
            }
            else
            {
                variable.delete();
            }
        }

        private PsiDeclarationStatement createNewDeclaration(
                @NotNull PsiVariable variable,
                @Nullable PsiExpression initializer)
                throws IncorrectOperationException
        {
            final PsiManager manager = variable.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
	        String name = variable.getName();
	        if (name == null)
	        {
		        name = "";
	        }
            final String comment = getCommentText(variable);
            final PsiType type = variable.getType();
            final String statementText;
            final String typeText = type.getCanonicalText();
            if (initializer == null)
            {
                statementText = typeText + ' ' + name +
                                ';' + comment;
            }
            else
            {
                final String initializerText = initializer.getText();
                statementText = typeText + ' ' + name +
                                '=' + initializerText + ';' + comment;
            }
            final PsiDeclarationStatement newDeclaration =
                    (PsiDeclarationStatement)factory.createStatementFromText(
                            statementText, variable);
            final PsiLocalVariable newVariable =
                    (PsiLocalVariable)newDeclaration.getDeclaredElements()[0];
            final PsiModifierList newModifierList =
                    newVariable.getModifierList();
            final PsiModifierList modifierList = variable.getModifierList();
            if (newModifierList != null && modifierList != null)
            {
                // remove final when PsiDeclarationFactory adds one by mistake
                newModifierList.setModifierProperty(PsiModifier.FINAL,
                        variable.hasModifierProperty(PsiModifier.FINAL));
                final PsiAnnotation[] annotations =
                        modifierList.getAnnotations();
                for (PsiAnnotation annotation : annotations)
                {
                    newModifierList.add(annotation);
                }
            }
            return newDeclaration;
        }

        private String getCommentText(PsiVariable variable) {
            final PsiDeclarationStatement parentDeclaration =
                    (PsiDeclarationStatement)variable.getParent();
            final PsiElement[] declaredElements =
                    parentDeclaration.getDeclaredElements();
            if (declaredElements.length != 1) {
                return "";
            }
            final PsiElement lastChild = parentDeclaration.getLastChild();
            if (!(lastChild instanceof PsiComment)) {
                return "";
            }
            final PsiElement prevSibling = lastChild.getPrevSibling();
            if (prevSibling instanceof PsiWhiteSpace) {
                return prevSibling.getText() + lastChild.getText();
            }
            return lastChild.getText();
        }

        private PsiDeclarationStatement moveDeclarationToLocation(
                @NotNull PsiVariable variable, @NotNull PsiElement location)
        throws IncorrectOperationException
        {
            PsiStatement statement = PsiTreeUtil.getParentOfType(location,
                    PsiStatement.class, false);
            assert statement != null;
            PsiElement statementParent = statement.getParent();
            while (statementParent instanceof PsiStatement &&
                   !(statementParent instanceof PsiForStatement))
            {
                statement = (PsiStatement)statementParent;
                statementParent = statement.getParent();
            }
            assert statementParent != null;
            final PsiExpression initializer = variable.getInitializer();
            if (isMoveable(initializer) &&
                statement instanceof PsiExpressionStatement)
            {
                final PsiExpressionStatement expressionStatement =
                        (PsiExpressionStatement)statement;
                final PsiExpression expression =
                        expressionStatement.getExpression();
                if (expression instanceof PsiAssignmentExpression)
                {
                    final PsiAssignmentExpression assignmentExpression =
                            (PsiAssignmentExpression)expression;
                    final PsiExpression lExpression =
                            assignmentExpression.getLExpression();
                    if (location.equals(lExpression))
                    {
                        PsiDeclarationStatement newDeclaration=
                                createNewDeclaration(variable,
                                        assignmentExpression.getRExpression());
                        newDeclaration = (PsiDeclarationStatement)
                                statementParent.addBefore(newDeclaration,
                                        statement);
                        final PsiElement parent =
                                assignmentExpression.getParent();
                        assert parent != null;
                        parent.delete();
                        return newDeclaration;
                    }
                }
            }
            PsiDeclarationStatement newDeclaration =
                    createNewDeclaration(variable, initializer);
            if (statement instanceof PsiForStatement)
            {
                final PsiForStatement forStatement = (PsiForStatement)statement;
                final PsiStatement initialization =
                        forStatement.getInitialization();
                newDeclaration = (PsiDeclarationStatement)
                        forStatement.addBefore(newDeclaration, initialization);
                if (initialization != null)
                {
                    initialization.delete();
                }
                return newDeclaration;
            }
            else
            {
                return (PsiDeclarationStatement)
                        statementParent.addBefore(newDeclaration, statement);
            }
        }
    }

    private boolean isMoveable(PsiExpression expression)
    {
        if (expression == null)
        {
            return true;
        }
        if (PsiUtil.isConstantExpression(expression) ||
            PsiKeyword.NULL.equals(expression.getText()))
        {
            return true;
        }
        if (expression instanceof PsiNewExpression)
        {
            final PsiNewExpression newExpression =
                    (PsiNewExpression)expression;
            final PsiExpression[] arrayDimensions =
                    newExpression.getArrayDimensions();
            if (arrayDimensions.length > 0)
            {
                for (PsiExpression arrayDimension : arrayDimensions)
                {
                    if (!isMoveable(arrayDimension))
                    {
                        return false;
                    }
                }
                return true;
            }
            final PsiArrayInitializerExpression arrayInitializer =
                    newExpression.getArrayInitializer();
            boolean result = true;
            if (arrayInitializer != null)
            {
                final PsiExpression[] initializers =
                        arrayInitializer.getInitializers();
                for (final PsiExpression initializerExpression :
                        initializers)
                {
                    result &= isMoveable(initializerExpression);
                }
            }
            else if (!m_allowConstructorAsInitializer)
            {
                return false;
            }

            final PsiExpressionList argumentList =
                    newExpression.getArgumentList();
            if (argumentList == null)
            {
                return result;
            }
            final PsiExpression[] expressions =
                    argumentList.getExpressions();
            for (final PsiExpression argumentExpression : expressions)
            {
                result &= isMoveable(argumentExpression);
            }
            return result;
        }
        return false;
    }

    public BaseInspectionVisitor buildVisitor()
    {
        return new TooBroadScopeVisitor();
    }

    private class TooBroadScopeVisitor extends BaseInspectionVisitor
    {

        public void visitVariable(@NotNull PsiVariable variable)
        {
            super.visitVariable(variable);
            if (!(variable instanceof PsiLocalVariable))
            {
                return;
            }
            final PsiExpression initializer = variable.getInitializer();
            final boolean initializerIsMoveable = isMoveable(initializer);
            if (!initializerIsMoveable)
            {
                return;
            }
            final PsiElement variableScope =
                    PsiTreeUtil.getParentOfType(variable,
                            PsiCodeBlock.class, PsiForStatement.class);
            if (variableScope == null)
            {
                return;
            }
            final Query<PsiReference> query =
                    ReferencesSearch.search(variable, variable.getUseScope());
            final Collection<PsiReference> referencesCollection =
                    query.findAll();
            final int size = referencesCollection.size();
            if (size == 0)
            {
                return;
            }
	        final PsiElement[] referenceElements =
                    new PsiElement[referencesCollection.size()];
	        int index = 0;
	        for (PsiReference reference : referencesCollection)
	        {
		        final PsiElement referenceElement = reference.getElement();
		        referenceElements[index] = referenceElement;
		        index++;
	        }
            PsiElement commonParent =
                    ScopeUtils.getCommonParent(referenceElements);
	        if (commonParent == null)
            {
                return;
            }
            if (initializer != null)
            {
                commonParent =
                        ScopeUtils.moveOutOfLoops(commonParent, variableScope);
                if (commonParent == null)
                {
                    return;
                }
            }
            if (PsiTreeUtil.isAncestor(variableScope, commonParent, true))
            {
                registerVariableError(variable);
                return;
            }
            if (m_onlyLookAtBlocks)
            {
                return;
            }
            if (commonParent instanceof PsiForStatement)
            {
                return;
            }
            final PsiElement referenceElement = referenceElements[0];
            final PsiElement blockChild =
                    ScopeUtils.getChildWhichContainsElement(variableScope,
                                                            referenceElement);
            if (blockChild == null)
            {
                return;
            }
            final PsiElement insertionPoint =
                    ScopeUtils.findTighterDeclarationLocation(
                            blockChild, variable);
            if (insertionPoint == null)
            {
                if (!(blockChild instanceof PsiExpressionStatement))
                {
                    return;
                }
                final PsiExpressionStatement expressionStatement =
                        (PsiExpressionStatement)blockChild;
                final PsiExpression expression =
                        expressionStatement.getExpression();
                if (!(expression instanceof PsiAssignmentExpression))
                {
                    return;
                }
                final PsiAssignmentExpression assignmentExpression =
                        (PsiAssignmentExpression)expression;
                final PsiExpression lExpression =
                        assignmentExpression.getLExpression();
                if (!lExpression.equals(referenceElement))
                {
                    return;
                }
            }
	        if (insertionPoint != null && PsiUtil.isInJspFile(insertionPoint))
	        {
		        PsiElement elementBefore = insertionPoint.getPrevSibling();
		        elementBefore = PsiTreeUtil.skipSiblingsBackward(elementBefore,
				        PsiWhiteSpace.class);
		        if (elementBefore instanceof PsiDeclarationStatement)
		        {
			        final PsiElement variableParent = variable.getParent();
			        if (elementBefore.equals(variableParent))
			        {
				        return;
			        }
		        }
	        }
	        registerVariableError(variable);
        }
    }
}