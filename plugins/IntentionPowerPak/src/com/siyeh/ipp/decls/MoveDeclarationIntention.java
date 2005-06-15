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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveDeclarationIntention extends Intention{
    protected PsiElementPredicate getElementPredicate(){
        return new MoveDeclarationPredicate();
    }

    public String getText(){
        return "Narrow scope of local variable";
    }

    public String getFamilyName(){
        return "Narrow Scope of Local Variable";
    }

    public void processIntention(@NotNull PsiElement element)
            throws IncorrectOperationException{
        final PsiLocalVariable variable = (PsiLocalVariable) element;
        final PsiManager manager = variable.getManager();
        final PsiSearchHelper searchHelper = manager.getSearchHelper();
        final PsiReference[] references =
                searchHelper.findReferences(variable, variable.getUseScope(),
                                            false);
        final PsiElement commonParent = MoveDeclarationPredicate.getCommonParent(references);
        assert commonParent != null;
        final PsiDeclarationStatement declaration = (PsiDeclarationStatement) variable.getParent();

        final PsiReference firstReference = references[0];
        final PsiElement referenceElement = firstReference.getElement();
        final PsiElement containingBlock;
        if(commonParent instanceof PsiExpressionStatement){
            containingBlock = commonParent;
        } else{
            containingBlock = PsiTreeUtil.getParentOfType(referenceElement,
                                                          PsiCodeBlock.class);
            assert containingBlock != null;
        }

        PsiDeclarationStatement newDeclaration;
        if(containingBlock.equals(commonParent)){
            // containing block of first reference is the same as the common block of all.
            newDeclaration = moveDeclarationToReference(referenceElement,
                                                        variable);
        } else{
            // declaration must be moved to common block (first reference block is too deep)
            final PsiElement child =
                    MoveDeclarationPredicate.getChildWhichContainsElement(commonParent,
                                                                          referenceElement);
            newDeclaration = createNewDeclaration(variable, null);
            newDeclaration = (PsiDeclarationStatement) commonParent.addBefore(newDeclaration,
                                                                              child);
        }

        if(declaration.getDeclaredElements().length == 1){
            declaration.delete();
        } else{
            variable.delete();
        }
        highlightElement(newDeclaration);
    }

    private static void highlightElement(@NotNull PsiElement element){
        final Project project = element.getProject();
        final FileEditorManager editorManager = FileEditorManager.getInstance(project);
        final HighlightManager highlightManager = HighlightManager.getInstance(project);
        final EditorColorsManager editorColorsManager = EditorColorsManager.getInstance();

        final Editor editor = editorManager.getSelectedTextEditor();
        final EditorColorsScheme globalScheme = editorColorsManager.getGlobalScheme();
        final TextAttributes textattributes =
                globalScheme .getAttributes(EditorColors.SEARCH_RESULT_ATTRIBUTES);
        final PsiElement[] elements = new PsiElement[]{element};
        highlightManager.addOccurrenceHighlights(editor, elements,
                                                 textattributes, true, null);

        final WindowManager windowManager = WindowManager.getInstance();
        final StatusBar statusBar = windowManager.getStatusBar(project);
        statusBar.setInfo("Press Escape to remove the highlighting");
    }

    private PsiDeclarationStatement moveDeclarationToReference(@NotNull PsiElement referenceElement,
                                                               @NotNull PsiLocalVariable variable
    )
            throws IncorrectOperationException{
        PsiStatement statement =
                PsiTreeUtil.getParentOfType(referenceElement,
                                            PsiStatement.class);
        assert statement != null;
        PsiElement statementParent = statement.getParent();
        while(statementParent instanceof PsiStatement &&
                !(statementParent instanceof PsiForStatement)){
            statement = (PsiStatement) statementParent;
            statementParent = statement.getParent();
        }

        final PsiElement referenceParent = referenceElement.getParent();
        final PsiElement referenceGrandParent = referenceParent.getParent();
        if(referenceParent instanceof PsiAssignmentExpression &&
                referenceGrandParent instanceof PsiExpressionStatement){
            final PsiAssignmentExpression assignmentExpression =
                    (PsiAssignmentExpression) referenceParent;
            if(referenceElement.equals(assignmentExpression.getLExpression())){
                PsiDeclarationStatement newDeclaration;
                newDeclaration = createNewDeclaration(variable,
                                                      assignmentExpression.getRExpression());
                newDeclaration = (PsiDeclarationStatement) statementParent.addBefore(newDeclaration,
                                                                                     statement);
                final PsiElement parent = assignmentExpression.getParent();
                parent.delete();
                return newDeclaration;
            }
        }
        final PsiDeclarationStatement newDeclaration = createNewDeclaration(variable,
                                                                            null);
        return (PsiDeclarationStatement) statementParent.addBefore(newDeclaration,
                                                                   statement);
    }

    private static PsiDeclarationStatement createNewDeclaration(@NotNull PsiLocalVariable variable,
                                                                @Nullable PsiExpression initializer)
            throws IncorrectOperationException{

        final PsiManager manager = variable.getManager();
        final PsiElementFactory factory = manager.getElementFactory();
        final PsiDeclarationStatement newDeclaration =
                factory.createVariableDeclarationStatement(
                        variable.getName(), variable.getType(), initializer,
                        false);
        final PsiLocalVariable newVariable =
                (PsiLocalVariable) newDeclaration.getDeclaredElements()[0];
        final PsiModifierList newModifierList = newVariable.getModifierList();

        final PsiModifierList modifierList = variable.getModifierList();
        if(modifierList.hasExplicitModifier(PsiModifier.FINAL)){
            newModifierList.setModifierProperty(PsiModifier.FINAL, true);
        } else{
            // remove final when PsiDeclarationFactory adds one by mistake
            newModifierList.setModifierProperty(PsiModifier.FINAL, false);
        }

        final PsiAnnotation[] annotations = modifierList.getAnnotations();
        for(PsiAnnotation annotation : annotations){
            newModifierList.add(annotation);
        }
        return newDeclaration;
    }
}