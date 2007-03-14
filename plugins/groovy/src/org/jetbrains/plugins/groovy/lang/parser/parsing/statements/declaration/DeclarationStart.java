package org.jetbrains.plugins.groovy.lang.parser.parsing.statements.declaration;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.plugins.groovy.lang.parser.parsing.Construction;
import org.jetbrains.plugins.groovy.lang.parser.parsing.util.ParserUtils;

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
