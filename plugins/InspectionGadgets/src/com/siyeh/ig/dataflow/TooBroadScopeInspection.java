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
package com.siyeh.ig.dataflow;

import com.intellij.codeInsight.daemon.GroupNames;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.find.FindManager;
import com.intellij.find.FindModel;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.Query;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import com.siyeh.ig.InspectionGadgetsFix;
import com.siyeh.ig.StatementInspection;
import com.siyeh.ig.StatementInspectionVisitor;
import com.siyeh.ig.ui.MultipleCheckboxOptionsPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.JComponent;
import java.util.Collection;

public class TooBroadScopeInspection extends StatementInspection
{

    /** @noinspection PublicField for externalization*/
    public boolean m_allowConstructorAsInitializer = false;
    /** @noinspection PublicField for externalization*/
    public boolean m_onlyLookAtBlocks = false;

    public String getDisplayName()
    {
        return InspectionGadgetsBundle.message("too.broad.scope.display.name");
    }

    public String getID()
    {
        return "TooBroadScope";
    }


    public String getGroupDisplayName()
    {
        return GroupNames.DATA_FLOW_ISSUES;
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

    private static class TooBroadScopeInspectionFix extends InspectionGadgetsFix
    {
        private String m_name;

        TooBroadScopeInspectionFix(String name)
        {
            super();
            m_name = name;
        }

        @NotNull
        public String getName()
        {
            return InspectionGadgetsBundle.message(
                    "too.broad.scope.narrow.quickfix", m_name);
        }

        protected void doFix(Project project, ProblemDescriptor descriptor)
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
            final PsiReference[] references =
                    referenceCollection.toArray(
                            new PsiReference[referenceCollection.size()]);
            PsiElement commonParent = ScopeUtils.getCommonParent(references);
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
            final PsiReference firstReference = references[0];
            final PsiElement referenceElement = firstReference.getElement();
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
            highlightElement(newDeclaration);
        }

        private static void removeOldVariable(@NotNull PsiVariable variable)
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

        private static PsiDeclarationStatement createNewDeclaration(
                @NotNull PsiVariable variable,
                @Nullable PsiExpression initializer)
                throws IncorrectOperationException
        {

            final PsiManager manager = variable.getManager();
            final PsiElementFactory factory = manager.getElementFactory();
            final PsiDeclarationStatement newDeclaration =
                    factory.createVariableDeclarationStatement(
                            variable.getName(), variable.getType(),
                            initializer);
            final PsiLocalVariable newVariable =
                    (PsiLocalVariable)newDeclaration.getDeclaredElements()[0];
            final PsiModifierList newModifierList =
                    newVariable.getModifierList();
            final PsiModifierList modifierList = variable.getModifierList();
            // remove final when PsiDeclarationFactory adds one by mistake
            newModifierList.setModifierProperty(PsiModifier.FINAL,
                    variable.hasModifierProperty(PsiModifier.FINAL));
            final PsiAnnotation[] annotations = modifierList.getAnnotations();
            for (PsiAnnotation annotation : annotations)
            {
                newModifierList.add(annotation);
            }
            return newDeclaration;
        }

        private static void highlightElement(@NotNull PsiElement element)
        {
            final Project project = element.getProject();
            final FileEditorManager editorManager =
                    FileEditorManager.getInstance(project);
            final HighlightManager highlightManager =
                    HighlightManager.getInstance(project);
            final EditorColorsManager editorColorsManager =
                    EditorColorsManager.getInstance();
            final Editor editor = editorManager.getSelectedTextEditor();
            final EditorColorsScheme globalScheme =
                    editorColorsManager.getGlobalScheme();
            final TextAttributes textattributes =
                    globalScheme.getAttributes(
                            EditorColors.SEARCH_RESULT_ATTRIBUTES);
            final PsiElement[] elements = new PsiElement[]{element};
            highlightManager.addOccurrenceHighlights(
                    editor, elements, textattributes, true, null);
            final WindowManager windowManager = WindowManager.getInstance();
            final StatusBar statusBar = windowManager.getStatusBar(project);
            statusBar.setInfo(InspectionGadgetsBundle.message(
                    "too.broad.scope.status.bar.remove.highlighting.message"));
            final FindManager findmanager = FindManager.getInstance(project);
            FindModel findmodel = findmanager.getFindNextModel();
            if(findmodel == null)
            {
                findmodel = findmanager.getFindInFileModel();
            }
            findmodel.setSearchHighlighters(true);
            findmanager.setFindWasPerformed();
            findmanager.setFindNextModel(findmodel);
        }

        private static PsiDeclarationStatement moveDeclarationToLocation(
                @NotNull PsiVariable variable, @NotNull PsiElement location)
        throws IncorrectOperationException
        {
            PsiStatement statement =
                    PsiTreeUtil.getParentOfType(location,
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
            if (initializer == null &&
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

    public BaseInspectionVisitor buildVisitor()
    {
        return new TooBroadScopeVisitor();
    }

    private class TooBroadScopeVisitor extends StatementInspectionVisitor
    {

        public void visitVariable(@NotNull PsiVariable variable)
        {
            super.visitVariable(variable);
            if (!(variable instanceof PsiLocalVariable))
            {
                return;
            }
            final PsiExpression initializer = variable.getInitializer();
            if (!isMoveable(initializer))
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
            final PsiReference[] references =
                    referencesCollection.toArray(new PsiReference[size]);
            PsiElement commonParent = ScopeUtils.getCommonParent(references);
            if (commonParent == null)
            {
                return;
            }
            if (initializer != null)
            {
                commonParent = ScopeUtils.moveOutOfLoops(
                        commonParent, variableScope);
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
            final PsiReference firstReference = references[0];
            final PsiElement referenceElement = firstReference.getElement();
            if (referenceElement == null)
            {
                return;
            }
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
                if (initializer != null)
                {
                    return;
                }
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
                if (!lExpression.equals(firstReference))
                {
                    return;
                }
            }
            final String blockChildText = blockChild.getText();
            if (blockChildText.startsWith("<%=") &&
                    blockChildText.endsWith("%>"))
            {
                // workaround because JspExpressionStatement is not part of
                // the openapi.

                final PsiElement element =
                        PsiTreeUtil.skipSiblingsBackward(insertionPoint,
                                PsiWhiteSpace.class, PsiComment.class);
                if (variable.equals(element))
                {
                    return;
                }
                return;
            }
            registerVariableError(variable);
        }

        private boolean isMoveable(PsiExpression expression)
        {
            if (expression == null)
            {
                return true;
            }
            if (PsiUtil.isConstantExpression(expression))
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
    }
}