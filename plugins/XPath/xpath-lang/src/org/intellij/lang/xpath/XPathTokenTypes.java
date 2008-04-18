/*
 * Copyright 2005 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.xpath;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;

public final class XPathTokenTypes {
    public static final IElementType WHITESPACE = TokenType.WHITE_SPACE;
    public static final IElementType BAD_CHARACTER = TokenType.BAD_CHARACTER;

    public static final IElementType PLUS = new XPathElementType("PLUS");   // +
    public static final IElementType MINUS = new XPathElementType("MINUS"); // -
    public static final IElementType MULT = new XPathElementType("MULT");   // *
    public static final IElementType DIV = new XPathElementType("DIV");     // div
    public static final IElementType MOD = new XPathElementType("MOD");     // mod
    public static final IElementType UNION = new XPathElementType("UNION"); // |
    public static final IElementType STAR = new XPathElementType("STAR");   // *

    public static final IElementType OR = new XPathElementType("OR");       // or
    public static final IElementType AND = new XPathElementType("AND");     // and
    public static final IElementType EQ = new XPathElementType("EQ");       // =
    public static final IElementType LE = new XPathElementType("LE");       // <=
    public static final IElementType LT = new XPathElementType("LT");       // <
    public static final IElementType GT = new XPathElementType("GT");       // >
    public static final IElementType GE = new XPathElementType("GE");       // >=
    public static final IElementType NE = new XPathElementType("NE");       // !=

    public static final IElementType DOT = new XPathElementType("DOT");                 // .
    public static final IElementType DOTDOT = new XPathElementType("DOTDOT");           // ..
    public static final IElementType AT = new XPathElementType("AT");                   // @
    public static final IElementType COLCOL = new XPathElementType("COLCOL");           // ::

    public static final IElementType AXIS_NAME = new XPathElementType("AXIS_NAME");         // axis-name / '::'
    public static final IElementType BAD_AXIS_NAME = new XPathElementType("BAD_AXIS_NAME"); // NCName / '::'
    public static final TokenSet AXIS = TokenSet.create(AXIS_NAME, BAD_AXIS_NAME);

    public static final IElementType COMMA = new XPathElementType("COMMA");         // ,

    public static final IElementType PATH = new XPathElementType("PATH");           // /
    public static final IElementType ANY_PATH = new XPathElementType("ANY_PATH");   // //

    public static final IElementType LBRACKET = new XPathElementType("LBRACKET");
    public static final IElementType RBRACKET = new XPathElementType("RBRACKET");

    public static final IElementType DOLLAR = new XPathElementType("DOLLAR");                       // $
    public static final IElementType VARIABLE_PREFIX = new XPathElementType("VARIABLE_PREFIX");     // <VAR> NCName / ':'
    public static final IElementType VARIABLE_NAME = new XPathElementType("VARIABLE_NAME");         // <VAR> NCName

    public static final IElementType LPAREN = new XPathElementType("LPAREN");           // (
    public static final IElementType RPAREN = new XPathElementType("RPAREN");           // )
    public static final IElementType STRING_LITERAL = new XPathElementType("STRING");   // "...", '...'
    public static final IElementType NUMBER = new XPathElementType("NUMBER");           //

    public static final IElementType NCNAME = new XPathElementType("NCNAME");   // NCName
    public static final IElementType COL = new XPathElementType("COL");         // :

    public static final IElementType LBRACE = new XPathElementType("LBRACE");           // {
    public static final IElementType RBRACE = new XPathElementType("RBRACE");           // }

    public static final IElementType NODE_TYPE = new XPathElementType("NODE_TYPE");         // (comment | text | pi | node) / '('
    public static final IElementType FUNCTION_NAME = new XPathElementType("FUNCTION_NAME"); // NCName / '('
    public static final IElementType EXT_PREFIX = new XPathElementType("EXT_PREFIX");       // NCName / ":" NCName "("

    public static final TokenSet EQUALITY_OPS = TokenSet.create(EQ, NE);
    public static final TokenSet REL_OPS = TokenSet.create(LT, GT, LE, GE);
    public static final TokenSet ADD_OPS = TokenSet.create(PLUS, MINUS);
    public static final TokenSet MUL_OPS = TokenSet.create(MULT, DIV, MOD);
    public static final TokenSet PATH_OPS = TokenSet.create(PATH, ANY_PATH);

    public static final TokenSet BOOLEAN_OPERATIONS = TokenSet.orSet(EQUALITY_OPS, REL_OPS, TokenSet.create(AND, OR));
    public static final TokenSet NUMBER_OPERATIONS = TokenSet.orSet(ADD_OPS, MUL_OPS);
    public static final TokenSet BINARY_OPERATIONS = TokenSet.orSet(BOOLEAN_OPERATIONS, NUMBER_OPERATIONS, TokenSet.create(UNION));

    public static final TokenSet KEYWORDS = TokenSet.create(AND, OR, MOD, DIV, AXIS_NAME, NODE_TYPE, STAR);

    public static final TokenSet REST = TokenSet.create(WHITESPACE, DOT, DOT, AT, COL, COLCOL, COMMA, PATH, ANY_PATH);

    private XPathTokenTypes() {
    }
}
