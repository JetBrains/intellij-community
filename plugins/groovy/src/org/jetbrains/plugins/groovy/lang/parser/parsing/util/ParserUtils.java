package org.jetbrains.plugins.groovy.lang.parser.parsing.util;

import com.intellij.lang.PsiBuilder;
import com.intellij.psi.tree.IElementType;

/**
 * Utility class, that contains various useful methods for
 * parser needs.
 *
 * @author Ilya.Sergey
 */
public abstract class ParserUtils {

    /**
     * Auxiliary method for strict token appearance
     *
     * @param builder  current builder
     * @param elem     given element
     * @param errorMsg Message, that displays if element was not found; if errorMsg == null nothing displays
     * @return true if element parsed
     */
    public static boolean tokenStrict(PsiBuilder builder, IElementType elem, String errorMsg) {
        if (elem.equals(builder.getTokenType())) {
            builder.advanceLexer();
            return true;
        } else {
            if (errorMsg != null)
                builder.error(errorMsg);
            return false;
        }
    }

    /**
     * Auxiliary method for construction like
     * <BNF>
     * token?
     * </BNF>
     * parsing
     *
     * @param builder current builder
     * @param elem    given element
     * @return true if element parsed
     */
    public static boolean tokenStrict(PsiBuilder builder, IElementType elem) {
        return tokenStrict(builder, elem, null);
    }
}
