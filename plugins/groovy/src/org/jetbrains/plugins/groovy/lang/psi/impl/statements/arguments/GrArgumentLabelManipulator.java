package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.resolve.GroovyStringLiteralManipulator;

/**
 * @author peter
 */
public class GrArgumentLabelManipulator extends AbstractElementManipulator<GrArgumentLabel> {
  @Override
  public TextRange getRangeInElement(GrArgumentLabel element) {
    final PsiElement nameElement = element.getNameElement();
    if (nameElement instanceof LeafPsiElement && GroovyTokenTypes.STRING_LITERAL_SET.contains(((LeafPsiElement)nameElement).getElementType())) {
      return GroovyStringLiteralManipulator.getLiteralRange(nameElement.getText());
    }

    return super.getRangeInElement(element);
  }

  public GrArgumentLabel handleContentChange(GrArgumentLabel element, TextRange range, String newContent)
    throws IncorrectOperationException {
    return (GrArgumentLabel)element.handleElementRename(newContent);
  }
}
