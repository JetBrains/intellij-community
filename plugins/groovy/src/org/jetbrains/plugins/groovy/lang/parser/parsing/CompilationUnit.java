package org.jetbrains.plugins.groovy.lang.parser.parsing;

import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import com.intellij.lang.PsiBuilder;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 05.03.2007
 */
public class CompilationUnit implements Construction {
    public GroovyElementType parse(PsiBuilder builder) {
        return GroovyElementTypes.NONE;
    }
}
