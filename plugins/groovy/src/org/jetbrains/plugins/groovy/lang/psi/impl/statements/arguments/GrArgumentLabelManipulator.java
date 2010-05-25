package org.jetbrains.plugins.groovy.lang.psi.impl.statements.arguments;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.AbstractElementManipulator;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;

/**
 * @author peter
 */
public class GrArgumentLabelManipulator extends AbstractElementManipulator<GrArgumentLabel> {
  public GrArgumentLabel handleContentChange(GrArgumentLabel element, TextRange range, String newContent)
    throws IncorrectOperationException {
    return (GrArgumentLabel)element.handleElementRename(newContent);
  }
}
