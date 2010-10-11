package org.jetbrains.javafx.lang.validation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiElement;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.javafx.JavaFxBundle;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;
import org.jetbrains.javafx.lang.psi.JavaFxLiteralExpression;
import org.jetbrains.javafx.lang.psi.JavaFxStringCompoundElement;
import org.jetbrains.javafx.lang.psi.JavaFxStringExpression;
import org.jetbrains.javafx.lang.psi.JavaFxUnaryExpression;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class LiteralAnnotatingVisitor extends JavaFxAnnotatingVisitor {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.javafx.lang.validation.LiteralAnnotatingVisitor");

  @Override
  public void visitLiteralExpression(JavaFxLiteralExpression node) {
    final IElementType elementType = node.getNode().getFirstChildNode().getElementType();
    if (elementType == JavaFxTokenTypes.INTEGER_LITERAL) {
      final PsiElement psiElement = getNode(node);
      try {
        Integer.parseInt(psiElement.getText());
      }
      catch (NumberFormatException e) {
        markError(psiElement, JavaFxBundle.message("integer.too.large"));
      }
    }
    else if (elementType == JavaFxTokenTypes.NUMBER_LITERAL) {
      final PsiElement psiElement = getNode(node);
      try {
        Double.parseDouble(psiElement.getText());
      }
      catch (NumberFormatException e) {
        markError(psiElement, JavaFxBundle.message("number.too.large"));
      }
    }
  }

  private static PsiElement getNode(JavaFxLiteralExpression node) {
    final PsiElement parent = node.getParent();
    if (parent instanceof JavaFxUnaryExpression) {
      if (parent.getNode().getFirstChildNode().getElementType() == JavaFxTokenTypes.MINUS) {
        return parent;
      }
    }
    return node;
  }

  @Override
  public void visitStringExpression(JavaFxStringExpression node) {
    final JavaFxStringCompoundElement[] stringCompoundElements = node.getStringElements();
    for (JavaFxStringCompoundElement compoundElement : stringCompoundElements) {
      final char startQuote = compoundElement.getText().charAt(0);
      LOG.assertTrue(startQuote == '\"' || startQuote == '\'');
      final ASTNode[] stringNodes = compoundElement.getStringNodes();
      final ASTNode lastNode = stringNodes[stringNodes.length - 1];
      final String lastNodeText = lastNode.getText();
      final int textLength = lastNodeText.length() - 1;
      if (textLength == 0) {
        markError(compoundElement, JavaFxBundle.message("missing.closing.quote"));
        continue;
      }
      int i = 0;
      while (i < textLength) {
        final char c = lastNodeText.charAt(i);
        if (c == '\\') {
          ++i;
        }
        ++i;
      }
      if (i != textLength || lastNodeText.charAt(i) != startQuote) {
        markError(compoundElement, JavaFxBundle.message("missing.closing.quote"));
      }
    }
  }
}
