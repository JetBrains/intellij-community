/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.javafx.lang.lexer;

import com.intellij.psi.TokenType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.javafx.lang.JavaFxElementType;

/**
 * JavaFx-specific tokens
 *
 * @author andrey, Alexey.Iavnov
 */
public interface JavaFxTokenTypes extends TokenType {
  JavaFxElementType IDENTIFIER = new JavaFxElementType("IDENTIFIER");
  JavaFxElementType C_STYLE_COMMENT = new JavaFxElementType("C_STYLE_COMMENT");
  JavaFxElementType END_OF_LINE_COMMENT = new JavaFxElementType("END_OF_LINE_COMMENT");
  JavaFxElementType DOC_COMMENT = new JavaFxElementType("DOC_COMMENT");

  JavaFxElementType INTEGER_LITERAL = new JavaFxElementType("INTEGER_LITERAL");
  JavaFxElementType NUMBER_LITERAL = new JavaFxElementType("NUMBER_LITERAL");
  JavaFxElementType STRING_LITERAL = new JavaFxElementType("STRING_LITERAL");
  JavaFxElementType LBRACE_STRING_LITERAL = new JavaFxElementType("LBRACE_STRING_LITERAL");
  JavaFxElementType LBRACE_RBRACE_STRING_LITERAL = new JavaFxElementType("LBRACE_RBRACE_STRING_LITERAL");
  JavaFxElementType RBRACE_STRING_LITERAL = new JavaFxElementType("RBRACE_STRING_LITERAL");
  JavaFxElementType DURATION_LITERAL = new JavaFxElementType("DURATION_LITERAL");

  JavaFxElementType LOCALIZATION_PREFIX = new JavaFxElementType("LOCALIZATION_PREFIX");

  /* **************************************************************************************************
  *  Keywords
  * ****************************************************************************************************/

  JavaFxElementType ABSTRACT_KEYWORD = new JavaFxElementType("abstract");
  JavaFxElementType AFTER_KEYWORD = new JavaFxElementType("after");
  JavaFxElementType AND_KEYWORD = new JavaFxElementType("and");
  JavaFxElementType AS_KEYWORD = new JavaFxElementType("as");
  JavaFxElementType ASSERT_KEYWORD = new JavaFxElementType("assert");
  JavaFxElementType AT_KEYWORD = new JavaFxElementType("at");
  JavaFxElementType ATTRIBUTE_KEYWORD = new JavaFxElementType("attribute");
  JavaFxElementType BEFORE_KEYWORD = new JavaFxElementType("before");
  JavaFxElementType BIND_KEYWORD = new JavaFxElementType("bind");
  JavaFxElementType BOUND_KEYWORD = new JavaFxElementType("bound");
  JavaFxElementType BREAK_KEYWORD = new JavaFxElementType("break");
  JavaFxElementType CATCH_KEYWORD = new JavaFxElementType("catch");
  JavaFxElementType CLASS_KEYWORD = new JavaFxElementType("class");
  JavaFxElementType CONTINUE_KEYWORD = new JavaFxElementType("continue");
  JavaFxElementType DEF_KEYWORD = new JavaFxElementType("def");
  JavaFxElementType DELETE_KEYWORD = new JavaFxElementType("delete");
  JavaFxElementType ELSE_KEYWORD = new JavaFxElementType("else");
  JavaFxElementType EXCLUSIVE_KEYWORD = new JavaFxElementType("exclusive");
  JavaFxElementType EXTENDS_KEYWORD = new JavaFxElementType("extends");
  JavaFxElementType FALSE_KEYWORD = new JavaFxElementType("false");
  JavaFxElementType FINALLY_KEYWORD = new JavaFxElementType("finally");
  JavaFxElementType FIRST_KEYWORD = new JavaFxElementType("first");
  JavaFxElementType FOR_KEYWORD = new JavaFxElementType("for");
  JavaFxElementType FROM_KEYWORD = new JavaFxElementType("from");
  JavaFxElementType FUNCTION_KEYWORD = new JavaFxElementType("function");
  JavaFxElementType IF_KEYWORD = new JavaFxElementType("if");
  JavaFxElementType IMPORT_KEYWORD = new JavaFxElementType("import");
  JavaFxElementType INDEXOF_KEYWORD = new JavaFxElementType("indexof");
  JavaFxElementType IN_KEYWORD = new JavaFxElementType("in");
  JavaFxElementType INIT_KEYWORD = new JavaFxElementType("init");
  JavaFxElementType INSERT_KEYWORD = new JavaFxElementType("insert");
  JavaFxElementType INSTANCEOF_KEYWORD = new JavaFxElementType("instanceof");
  JavaFxElementType INTO_KEYWORD = new JavaFxElementType("into");
  JavaFxElementType INVERSE_KEYWORD = new JavaFxElementType("inverse");
  JavaFxElementType LAST_KEYWORD = new JavaFxElementType("last");
  JavaFxElementType LAZY_KEYWORD = new JavaFxElementType("lazy");
  JavaFxElementType MIXIN_KEYWORD = new JavaFxElementType("mixin");
  JavaFxElementType MOD_KEYWORD = new JavaFxElementType("mod");
  JavaFxElementType NEW_KEYWORD = new JavaFxElementType("new");
  JavaFxElementType NOT_KEYWORD = new JavaFxElementType("not");
  JavaFxElementType NULL_KEYWORD = new JavaFxElementType("null");
  JavaFxElementType ON_KEYWORD = new JavaFxElementType("on");
  JavaFxElementType OR_KEYWORD = new JavaFxElementType("or");
  JavaFxElementType OVERRIDE_KEYWORD = new JavaFxElementType("override");
  JavaFxElementType PACKAGE_KEYWORD = new JavaFxElementType("package");
  JavaFxElementType POSTINIT_KEYWORD = new JavaFxElementType("postinit");
  JavaFxElementType PRIVATE_KEYWORD = new JavaFxElementType("private");
  JavaFxElementType PROTECTED_KEYWORD = new JavaFxElementType("protected");
  JavaFxElementType PUBLIC_INIT_KEYWORD = new JavaFxElementType("public-init");
  JavaFxElementType PUBLIC_KEYWORD = new JavaFxElementType("public");
  JavaFxElementType PUBLIC_READ_KEYWORD = new JavaFxElementType("public-read");
  JavaFxElementType REPLACE_KEYWORD = new JavaFxElementType("replace");
  JavaFxElementType INVALIDATE_KEYWORD = new JavaFxElementType("invalidate");
  JavaFxElementType RETURN_KEYWORD = new JavaFxElementType("return");
  JavaFxElementType REVERSE_KEYWORD = new JavaFxElementType("reverse");
  JavaFxElementType SIZEOF_KEYWORD = new JavaFxElementType("sizeof");
  JavaFxElementType STATIC_KEYWORD = new JavaFxElementType("static");
  JavaFxElementType STEP_KEYWORD = new JavaFxElementType("step");
  JavaFxElementType SUPER_KEYWORD = new JavaFxElementType("super");
  JavaFxElementType THEN_KEYWORD = new JavaFxElementType("then");
  JavaFxElementType THIS_KEYWORD = new JavaFxElementType("this");
  JavaFxElementType THROW_KEYWORD = new JavaFxElementType("throw");
  JavaFxElementType TRIGGER_KEYWORD = new JavaFxElementType("trigger");
  JavaFxElementType TRUE_KEYWORD = new JavaFxElementType("true");
  JavaFxElementType TRY_KEYWORD = new JavaFxElementType("try");
  JavaFxElementType TWEEN_KEYWORD = new JavaFxElementType("tween");
  JavaFxElementType TYPEOF_KEYWORD = new JavaFxElementType("typeof");
  JavaFxElementType VAR_KEYWORD = new JavaFxElementType("var");
  JavaFxElementType WHERE_KEYWORD = new JavaFxElementType("where");
  JavaFxElementType WHILE_KEYWORD = new JavaFxElementType("while");
  JavaFxElementType WITH_KEYWORD = new JavaFxElementType("with");

  JavaFxElementType LPAREN = new JavaFxElementType("LPAREN"); // )
  JavaFxElementType RPAREN = new JavaFxElementType("RPAREN");  // (
  JavaFxElementType LBRACE = new JavaFxElementType("LBRACE");  // {
  JavaFxElementType RBRACE = new JavaFxElementType("RBRACE");  // }
  JavaFxElementType LBRACK = new JavaFxElementType("LBRACK");  // ]
  JavaFxElementType RBRACK = new JavaFxElementType("RBRACK");  // [
  JavaFxElementType SEMICOLON = new JavaFxElementType("SEMICOLON");  // ;
  JavaFxElementType COMMA = new JavaFxElementType("COMMA");  // ,
  JavaFxElementType DOT = new JavaFxElementType("DOT");  // .
  JavaFxElementType COLON = new JavaFxElementType("COLON");  // :
  JavaFxElementType RANGE = new JavaFxElementType("RANGE");  // ..
  JavaFxElementType DELIM = new JavaFxElementType("DELIM");  // |

  JavaFxElementType EQEQ = new JavaFxElementType("EQEQ");  // ==
  JavaFxElementType LT = new JavaFxElementType("LT");  // <
  JavaFxElementType GT = new JavaFxElementType("GT");  // >
  JavaFxElementType NOTEQ = new JavaFxElementType("NOTEQ");  // !=
  JavaFxElementType LTEQ = new JavaFxElementType("LTEQ");  // <=
  JavaFxElementType GTEQ = new JavaFxElementType("GTEQ");  // >=
  JavaFxElementType EQGT = new JavaFxElementType("EQGT");  // EQGT
  JavaFxElementType EQ = new JavaFxElementType("EQ");  // EQ

  JavaFxElementType PLUSEQ = new JavaFxElementType("PLUSEQ");  // +=
  JavaFxElementType MINUSEQ = new JavaFxElementType("MINUSEQ");  // -=
  JavaFxElementType MULTEQ = new JavaFxElementType("MULTEQ");  // *=
  JavaFxElementType DIVEQ = new JavaFxElementType("DIVEQ");  // /=

  JavaFxElementType PLUS = new JavaFxElementType("PLUS");  // +
  JavaFxElementType MINUS = new JavaFxElementType("MINUS");  // -
  JavaFxElementType MULT = new JavaFxElementType("MULT");  // *
  JavaFxElementType DIV = new JavaFxElementType("DIV");  // /
  JavaFxElementType PLUSPLUS = new JavaFxElementType("PLUSPLUS");  // ++
  JavaFxElementType MINUSMINUS = new JavaFxElementType("MINUSMINUS");  // --

  //JavaFxElementType NUMBER = new JavaFxElementType("Number");
  //JavaFxElementType INTEGER = new JavaFxElementType("Integer");
  //JavaFxElementType BOOLEAN = new JavaFxElementType("Boolean");
  //JavaFxElementType DURATION = new JavaFxElementType("Duration");
  //JavaFxElementType VOID = new JavaFxElementType("Void");
  //JavaFxElementType STRING = new JavaFxElementType("String");

  TokenSet RESERVED_WORDS = TokenSet
    .create(ABSTRACT_KEYWORD, AFTER_KEYWORD, AND_KEYWORD, AS_KEYWORD, ASSERT_KEYWORD, AT_KEYWORD, ATTRIBUTE_KEYWORD, BEFORE_KEYWORD,
            BIND_KEYWORD, BOUND_KEYWORD, BREAK_KEYWORD, CATCH_KEYWORD, CLASS_KEYWORD, CONTINUE_KEYWORD, DEF_KEYWORD, DELETE_KEYWORD,
            ELSE_KEYWORD, EXCLUSIVE_KEYWORD, EXTENDS_KEYWORD, FALSE_KEYWORD, FINALLY_KEYWORD, FOR_KEYWORD, FROM_KEYWORD, FUNCTION_KEYWORD,
            IF_KEYWORD, IMPORT_KEYWORD, INDEXOF_KEYWORD, INSERT_KEYWORD, INSTANCEOF_KEYWORD, LAZY_KEYWORD, MIXIN_KEYWORD, MOD_KEYWORD,
            NEW_KEYWORD, NOT_KEYWORD, NULL_KEYWORD, OR_KEYWORD, OVERRIDE_KEYWORD, PACKAGE_KEYWORD, PRIVATE_KEYWORD, PROTECTED_KEYWORD,
            PUBLIC_INIT_KEYWORD, PUBLIC_KEYWORD, PUBLIC_READ_KEYWORD, RETURN_KEYWORD, REVERSE_KEYWORD, SIZEOF_KEYWORD, STATIC_KEYWORD,
            SUPER_KEYWORD, THEN_KEYWORD, THIS_KEYWORD, THROW_KEYWORD, TRUE_KEYWORD, TRY_KEYWORD, TYPEOF_KEYWORD, VAR_KEYWORD,
            WHILE_KEYWORD);

  TokenSet KEYWORDS = TokenSet
    .create(FIRST_KEYWORD, IN_KEYWORD, INIT_KEYWORD, INTO_KEYWORD, INVERSE_KEYWORD, LAST_KEYWORD, ON_KEYWORD, POSTINIT_KEYWORD,
            REPLACE_KEYWORD, INVALIDATE_KEYWORD, STEP_KEYWORD, TWEEN_KEYWORD, WHERE_KEYWORD, WITH_KEYWORD);

  TokenSet ALL_WORDS = TokenSet.orSet(RESERVED_WORDS, KEYWORDS);

  TokenSet NAME = TokenSet.orSet(TokenSet.create(IDENTIFIER), KEYWORDS);

  TokenSet NAME_ALL = TokenSet.orSet(TokenSet.create(IDENTIFIER), ALL_WORDS);

  TokenSet BRACES = TokenSet.create(LPAREN, RPAREN, LBRACE, RBRACE, LBRACK, RBRACK);

  TokenSet BLOCK_COMMENTS = TokenSet.create(C_STYLE_COMMENT, DOC_COMMENT);

  TokenSet LINE_COMMENTS = TokenSet.create(END_OF_LINE_COMMENT);

  TokenSet COMMENTS = TokenSet.create(C_STYLE_COMMENT, DOC_COMMENT, END_OF_LINE_COMMENT);

  TokenSet WHITESPACES = TokenSet.create(WHITE_SPACE);

  TokenSet NUMBERS = TokenSet.create(INTEGER_LITERAL, NUMBER_LITERAL, DURATION_LITERAL);

  TokenSet STRING_START = TokenSet.create(LOCALIZATION_PREFIX, STRING_LITERAL, LBRACE_STRING_LITERAL);

  TokenSet STRINGS = TokenSet.create(STRING_LITERAL, LBRACE_STRING_LITERAL, LBRACE_RBRACE_STRING_LITERAL, RBRACE_STRING_LITERAL);

  TokenSet ALL_STRINGS = TokenSet.orSet(STRINGS, TokenSet.create(LOCALIZATION_PREFIX));

  //TokenSet TYPES = TokenSet.create(BOOLEAN, DURATION, INTEGER, NUMBER, STRING, VOID);

  TokenSet MODIFIERS = TokenSet
    .create(PACKAGE_KEYWORD, PROTECTED_KEYWORD, PUBLIC_KEYWORD, PUBLIC_READ_KEYWORD, PUBLIC_INIT_KEYWORD, ABSTRACT_KEYWORD, BOUND_KEYWORD,
            OVERRIDE_KEYWORD, STATIC_KEYWORD, PRIVATE_KEYWORD, MIXIN_KEYWORD);

  TokenSet EQ_OPERATORS = TokenSet.create(PLUSEQ, MINUSEQ, MULTEQ, DIVEQ);
  TokenSet RELATIONAL_OPERATORS = TokenSet.create(EQEQ, NOTEQ, LTEQ, GTEQ, LT, GT);
  TokenSet BINARY_OPERATORS = TokenSet.orSet(RELATIONAL_OPERATORS, TokenSet
    .create(PLUS, MINUS, MULT, DIV, MOD_KEYWORD, OR_KEYWORD, AND_KEYWORD, AS_KEYWORD, INSTANCEOF_KEYWORD));
  TokenSet UNARY_OPERATORS = TokenSet.create(MINUS, NOT_KEYWORD, SIZEOF_KEYWORD, PLUSPLUS, MINUSMINUS, REVERSE_KEYWORD);
  TokenSet LITERALS =
    TokenSet.create(INTEGER_LITERAL, NUMBER_LITERAL, DURATION_LITERAL, NULL_KEYWORD, TRUE_KEYWORD, FALSE_KEYWORD);

  TokenSet VARIABLE_LABEL = TokenSet.create(VAR_KEYWORD, DEF_KEYWORD, ATTRIBUTE_KEYWORD);
}
