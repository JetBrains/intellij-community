package org.jetbrains.javafx.editor;

import com.intellij.lang.CodeDocumentationAwareCommenter;
import com.intellij.psi.PsiComment;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;

/**
 * Commenting support
 *
 * @author andrey
 */
public class JavaFxCommenter implements CodeDocumentationAwareCommenter, JavaFxTokenTypes {
  public String getLineCommentPrefix() {
    return "//";
  }

  public String getBlockCommentPrefix() {
    return "/*";
  }

  public String getBlockCommentSuffix() {
    return "*/";
  }

  public String getCommentedBlockCommentPrefix() {
    return null;
  }

  public String getCommentedBlockCommentSuffix() {
    return null;
  }

  @Nullable
  public IElementType getLineCommentTokenType() {
    return END_OF_LINE_COMMENT;
  }

  @Nullable
  public IElementType getBlockCommentTokenType() {
    return C_STYLE_COMMENT;
  }

  @Nullable
  public IElementType getDocumentationCommentTokenType() {
    return DOC_COMMENT;
  }

  @Nullable
  public String getDocumentationCommentPrefix() {
    return "/**";
  }

  @Nullable
  public String getDocumentationCommentLinePrefix() {
    return "*";
  }

  @Nullable
  public String getDocumentationCommentSuffix() {
    return "*/";
  }

  public boolean isDocumentationComment(PsiComment element) {
    return element.getText().startsWith(getDocumentationCommentPrefix());
  }
}
