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
package com.siyeh.ipp.decls;

import com.intellij.codeInsight.highlighting.HighlightManager;
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
import com.intellij.psi.search.PsiSearchHelper;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ipp.base.Intention;
import com.siyeh.ipp.base.PsiElementPredicate;
import com.siyeh.IntentionPowerPackBundle;
import org.jetbrains.annotations.NotNull;

public class MoveDeclarationIntention extends Intention {

    @NotNull
    protected PsiElementPredicate getElementPredicate() {
        return new MoveDeclarationPredicate();
    }

    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException {
        final PsiLocalVariable variable = (PsiLocalVariable)element;
        final PsiManager manager = variable.getManager();
        final PsiSearchHelper searchHelper = manager.getSearchHelper();
        final PsiReference[] references =
                searchHelper.findReferences(variable, variable.getUseScope(),
                        false);
        final PsiCodeBlock tightestBlock =
                MoveDeclarationPredicate.getTightestBlock(references);
        assert tightestBlock != null;
        final PsiDeclarationStatement declaration =
                (PsiDeclarationStatement)variable.getParent();
        final PsiReference firstReference = references[0];
        final PsiElement referenceElement = firstReference.getElement();
        PsiDeclarationStatement newDeclaration;
        if (tightestBlock.equals(PsiTreeUtil.getParentOfType(referenceElement,
                PsiCodeBlock.class))) {
            // containing block of first reference is the same as the common
            //  block of all.
            newDeclaration = moveDeclarationToReference(referenceElement,
                    variable,
                    tightestBlock);
        } else {
            // declaration must be moved to common block (first reference block
            // is too deep)
            final PsiElement child =
                    MoveDeclarationPredicate.getChildWhichContainsElement(
                            tightestBlock, referenceElement);
            newDeclaration = createNewDeclaration(variable, null);
            newDeclaration = (PsiDeclarationStatement)
                    tightestBlock.addBefore(newDeclaration, child);
        }
        assert declaration != null;
        if (declaration.getDeclaredElements().length == 1) {
            declaration.delete();
        } else {
            variable.delete();
        }
        highlightElement(newDeclaration);
    }

    private static void highlightElement(@NotNull PsiElement element) {
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
                globalScheme .getAttributes(
                        EditorColors.SEARCH_RESULT_ATTRIBUTES);
        final PsiElement[] elements = new PsiElement[]{element};
        highlightManager.addOccurrenceHighlights(editor, elements,
                textattributes, true, null);

        final WindowManager windowManager = WindowManager.getInstance();
        final StatusBar statusBar = windowManager.getStatusBar(project);
        statusBar.setInfo(IntentionPowerPackBundle.message(
                "status.bar.escape.highlighting.message"));
    }

    private static PsiDeclarationStatement moveDeclarationToReference(
            @NotNull PsiElement referenceElement,
            @NotNull PsiLocalVariable variable,
            @NotNull PsiCodeBlock block)
            throws IncorrectOperationException {
        PsiStatement statement =
                PsiTreeUtil.getParentOfType(referenceElement,
                        PsiStatement.class);
        assert statement != null;
        if (statement.getParent() instanceof PsiForStatement) {
            statement = (PsiStatement)statement.getParent();
        }
        final PsiElement referenceParent = referenceElement.getParent();
        if (referenceParent instanceof PsiAssignmentExpression) {
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression)referenceParent;
            if (referenceElement.equals(
                    assignmentExpression.getLExpression())) {
                PsiDeclarationStatement newDeclaration =
                        createNewDeclaration(variable,
                                assignmentExpression.getRExpression());
                newDeclaration = (PsiDeclarationStatement)
                        block.addBefore(newDeclaration,
                                statement);
                final PsiElement parent = assignmentExpression.getParent();
                assert parent != null;
                parent.delete();
                return newDeclaration;
            }
        }
        return createNewDeclaration(variable, null);
    }

    private static PsiDeclarationStatement createNewDeclaration(
            @NotNull PsiLocalVariable variable, PsiExpression initializer)
            throws IncorrectOperationException {
        final PsiManager manager = variable.getManager();
        final PsiElementFactory factory = manager.getElementFactory();
        final PsiDeclarationStatement newDeclaration =
                factory.createVariableDeclarationStatement(
                        variable.getName(), variable.getType(), initializer);
        if (variable.hasModifierProperty(PsiModifier.FINAL)) {
            final PsiLocalVariable newVariable =
                    (PsiLocalVariable)newDeclaration.getDeclaredElements()[0];
            final PsiModifierList modifierList = newVariable.getModifierList();
            modifierList.setModifierProperty(PsiModifier.FINAL, true);
        }
        return newDeclaration;
    }
}