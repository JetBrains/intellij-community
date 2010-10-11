package org.jetbrains.javafx.lang.validation;

import com.intellij.lang.ASTNode;
import com.intellij.lang.annotation.Annotation;
import org.jetbrains.javafx.JavaFxBundle;
import org.jetbrains.javafx.actions.intentions.RemovePrivateQuickFix;
import org.jetbrains.javafx.lang.lexer.JavaFxTokenTypes;
import org.jetbrains.javafx.lang.psi.JavaFxModifierList;
import org.jetbrains.javafx.lang.psi.JavaFxVariableDeclaration;

/**
 * Created by IntelliJ IDEA.
 * @author: Alexey.Ivanov
 */
public class JavaFxDeprecationVisitor extends JavaFxAnnotatingVisitor {
  @Override
  public void visitVariableDeclaration(final JavaFxVariableDeclaration node) {
    final ASTNode attributeKeyword = node.getNode().findChildByType(JavaFxTokenTypes.ATTRIBUTE_KEYWORD);
    if (attributeKeyword != null) {
      markError(attributeKeyword.getPsi(), JavaFxBundle.message("attribute.keyword.is.no.supported"));
    }
  }

  @Override
  public void visitModifierList(JavaFxModifierList node) {
    final ASTNode astNode = node.getNode();
    if (astNode != null) {
      final ASTNode privateKeyword = astNode.findChildByType(JavaFxTokenTypes.PRIVATE_KEYWORD);
      if (privateKeyword != null) {
        final Annotation annotation = markError(privateKeyword.getPsi(), JavaFxBundle.message("private.keyword.is.no.supported"));
        annotation.registerFix(new RemovePrivateQuickFix());
      }
      final ASTNode staticKeyword = astNode.findChildByType(JavaFxTokenTypes.STATIC_KEYWORD);
      if (staticKeyword != null) {
        getHolder().createWarningAnnotation(staticKeyword, JavaFxBundle.message("static.keyword.is.no.supported"));
      }
    }
  }
}
