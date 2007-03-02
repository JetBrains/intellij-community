package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;

/**
 * Utility interface that contains all Groovy non-token element types
 *
 * @author Ilya.Sergey
 */
public interface GroovyElementTypes {

  IElementType FILE = new GroovyElementType("Groovy file");
  
}
