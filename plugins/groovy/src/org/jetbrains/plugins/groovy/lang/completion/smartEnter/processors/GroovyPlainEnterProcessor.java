package org.jetbrains.plugins.groovy.lang.completion.smartEnter.processors;

import com.intellij.psi.PsiElement;
import com.intellij.codeInsight.editorActions.smartEnter.EnterProcessor;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.actionSystem.EditorActionHandler;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.actionSystem.IdeActions;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 05.08.2008
 */
public class GroovyPlainEnterProcessor implements EnterProcessor {
  public boolean doEnter(Editor editor, PsiElement psiElement, boolean isModified) {
    GrCodeBlock block = getControlStatementBlock(editor.getCaretModel().getOffset(), psiElement);

    if (block != null) {
      PsiElement firstElement = block.getFirstChild().getNextSibling();

      editor.getCaretModel().moveToOffset(firstElement != null ?
              firstElement.getTextRange().getStartOffset() -1  :
              block.getTextRange().getEndOffset());
    }

    getEnterHandler().execute(editor, ((EditorEx) editor).getDataContext());
    return true;
  }

  private EditorActionHandler getEnterHandler() {
    EditorActionHandler enterHandler = EditorActionManager.getInstance().getActionHandler(
            IdeActions.ACTION_EDITOR_START_NEW_LINE
    );
    return enterHandler;
  }

  private GrCodeBlock getControlStatementBlock(int caret, PsiElement element) {
    GrStatement body = null;
    if (element instanceof GrIfStatement) {
      body = ((GrIfStatement) element).getThenBranch();
      if (caret > body.getTextRange().getEndOffset()) {
        body = ((GrIfStatement) element).getElseBranch();
      }
    } else if (element instanceof GrWhileStatement) {
      body = ((GrWhileStatement) element).getBody();
    } else if (element instanceof GrForStatement) {
      body = ((GrForStatement) element).getBody();
    }

    return body instanceof GrBlockStatement ? ((GrBlockStatement) body).getBlock() : null;
  }
}
