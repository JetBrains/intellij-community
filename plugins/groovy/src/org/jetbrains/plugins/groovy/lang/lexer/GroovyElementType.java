package org.jetbrains.plugins.groovy.lang.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * Main class for Groovy element types, such as lexems or AST nodes
 *
 * @author Ilya.Sergey
 */
public class GroovyElementType extends IElementType {

  private String debugName = null;

  public GroovyElementType(String debugName) {
    super(debugName, GroovyFileType.GROOVY_FILE_TYPE.getLanguage());
    this.debugName = debugName;
  }

  public String toString() {
    return debugName;
  }
}
