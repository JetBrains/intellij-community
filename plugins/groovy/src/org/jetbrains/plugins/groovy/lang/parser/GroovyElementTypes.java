package org.jetbrains.plugins.groovy.lang.parser;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;

/**
 * Utility interface that contains all Groovy non-token element types
 *
 * @author Ilya.Sergey
 */
public interface GroovyElementTypes {

    GroovyElementType NONE = new GroovyElementType("there is no node");

    GroovyElementType FILE = new GroovyElementType("Groovy file");
    GroovyElementType COMPILATION_UNIT = new GroovyElementType("Compilation unit");

}
