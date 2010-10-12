package org.jetbrains.javafx.lang.psi.impl.resolve;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceElement;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxReferenceElementManipulator extends AbstractElementManipulator<JavaFxReferenceElement> {
  @Override
  public JavaFxReferenceElement handleContentChange(final JavaFxReferenceElement element, final TextRange range, final String newContent)
    throws IncorrectOperationException {
    return null;
  }

  @Override
  public TextRange getRangeInElement(final JavaFxReferenceElement element) {
    final ASTNode nameNode = element.getNameNode();
    final int startOffset = (nameNode != null) ? nameNode.getStartOffset() : element.getTextRange().getStartOffset();
    return new TextRange(startOffset - element.getTextRange().getStartOffset(), element.getTextLength());
  }
}
