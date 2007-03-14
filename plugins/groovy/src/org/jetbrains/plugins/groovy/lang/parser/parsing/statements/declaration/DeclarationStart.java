package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration;

import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyElementType;
import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;

/**
 * @autor: Dmitry.Krasilschikov
 * @date: 14.03.2007
 */
public class DeclarationStart implements Construction {

    public static IElementType parse(PsiBuilder builder) {
        if (ParserUtils.getToken(builder, kDEF)) return kDEF;
        return WRONGWAY;
    }
}
