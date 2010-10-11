package org.jetbrains.javafx.actions.intentions;

import com.intellij.codeInspection.IntentionAndQuickFixAction;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.JavaFxBundle;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class RemovePrivateQuickFix extends IntentionAndQuickFixAction {
  @NotNull
  @Override
  public String getName() {
    return JavaFxBundle.message("INTN.remove.private.keyword");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaFxBundle.message("INTN.remove.private.keyword");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, @Nullable Editor editor, PsiFile file) {
    if (editor != null) {
      final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      if (element != null) {
        final ASTNode node = element.getNode();
        return node != null && node.getElementType() == JavaFxTokenTypes.PRIVATE_KEYWORD;
      }
    }
    return false;
  }

  @Override
  public void applyFix(@NotNull final Project project, final PsiFile file, @Nullable final Editor editor)
    throws IncorrectOperationException {
    if (editor != null) {
      final PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
      if (element != null) {
        element.delete();
      }
    }
  }
}
