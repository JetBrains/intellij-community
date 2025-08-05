// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.tree.TokenSet;

import static com.intellij.psi.tree.TokenSet.create;
import static com.intellij.psi.tree.TokenSet.orSet;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;

public interface GroovyTokenSets {
  /**
   * Keywords that are always treated as keywords.
   */
  TokenSet RESERVED_KEYWORDS = create(
    KW_AS, KW_ASSERT, KW_BREAK, KW_CASE,
    KW_CATCH, KW_CLASS, /*const,*/ KW_CONTINUE,
    KW_DEF, KW_DEFAULT, KW_DO, KW_ELSE,
    KW_ENUM, KW_EXTENDS, KW_FALSE, KW_FINALLY,
    KW_FOR, /*goto,*/ KW_IF, KW_IMPLEMENTS,
    KW_IMPORT, KW_IN, T_NOT_IN, KW_INSTANCEOF, T_NOT_INSTANCEOF, KW_INTERFACE,
    KW_NEW, KW_NULL, KW_PACKAGE, KW_RETURN,
    KW_SUPER, KW_SWITCH, KW_THIS, KW_THROW,
    KW_THROWS, KW_TRAIT, KW_TRUE, KW_TRY,
    KW_WHILE, KW_YIELD
  );

  /**
   * http://docs.groovy-lang.org/latest/html/documentation/core-syntax.html#_keywords. Reserved + Contextual keywords.
   */
  TokenSet KEYWORDS = orSet(RESERVED_KEYWORDS, create(KW_PERMITS, KW_RECORD, KW_VAR, KW_YIELD));


  TokenSet STRING_LITERALS = create(STRING_SQ, STRING_TSQ, STRING_DQ, STRING_TDQ);

  TokenSet LOGICAL_OPERATORS = create(T_LAND, T_LOR, T_IMPL);
  TokenSet EQUALITY_OPERATORS = create(T_EQ, T_NEQ, T_ID, T_NID);
  TokenSet RELATIONAL_OPERATORS = create(T_GT, T_GE, T_LT, T_LE, T_COMPARE);
  TokenSet BITWISE_OPERATORS = create(T_BAND, T_BOR, T_XOR);
  TokenSet ADDITIVE_OPERATORS = create(T_PLUS, T_MINUS);
  TokenSet MULTIPLICATIVE_OPERATORS = create(T_STAR, T_DIV, T_REM);
  TokenSet SHIFT_OPERATORS = create(LEFT_SHIFT_SIGN, RIGHT_SHIFT_SIGN, RIGHT_SHIFT_UNSIGNED_SIGN);
  TokenSet REGEX_OPERATORS = create(T_REGEX_FIND, T_REGEX_MATCH);
  TokenSet RANGES = create(T_RANGE, T_RANGE_BOTH_OPEN, T_RANGE_LEFT_OPEN, T_RANGE_RIGHT_OPEN);
  TokenSet OTHER_OPERATORS = create(KW_AS, KW_IN, T_NOT_IN, T_POW, KW_INSTANCEOF, T_NOT_INSTANCEOF);
  TokenSet BINARY_OPERATORS = orSet(
    LOGICAL_OPERATORS,
    EQUALITY_OPERATORS,
    RELATIONAL_OPERATORS,
    BITWISE_OPERATORS,
    ADDITIVE_OPERATORS,
    MULTIPLICATIVE_OPERATORS,
    SHIFT_OPERATORS,
    REGEX_OPERATORS,
    RANGES,
    OTHER_OPERATORS
  );

  TokenSet OPERATOR_ASSIGNMENTS = create(
    T_POW_ASSIGN,
    T_STAR_ASSIGN,
    T_DIV_ASSIGN,
    T_REM_ASSIGN,
    T_PLUS_ASSIGN,
    T_MINUS_ASSIGN,
    T_LSH_ASSIGN,
    T_RSH_ASSIGN,
    T_RSHU_ASSIGN,
    T_BAND_ASSIGN,
    T_XOR_ASSIGN,
    T_BOR_ASSIGN
  );

  TokenSet ASSIGNMENTS = orSet(
    create(T_ASSIGN, T_ELVIS_ASSIGN),
    OPERATOR_ASSIGNMENTS
  );

  TokenSet REFERENCE_DOTS = create(T_DOT, T_SAFE_DOT, T_SAFE_CHAIN_DOT, T_SPREAD_DOT);
  TokenSet METHOD_REFERENCE_DOTS = create(T_METHOD_CLOSURE, T_METHOD_REFERENCE);
  TokenSet SAFE_DOTS = create(T_SAFE_DOT, T_SAFE_CHAIN_DOT);
  TokenSet DOTS = orSet(REFERENCE_DOTS, METHOD_REFERENCE_DOTS);
}
