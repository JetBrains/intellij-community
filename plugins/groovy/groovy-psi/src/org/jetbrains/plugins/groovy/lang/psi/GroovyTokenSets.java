// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.psi.tree.TokenSet;

import static com.intellij.psi.tree.TokenSet.create;
import static com.intellij.psi.tree.TokenSet.orSet;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;

public interface GroovyTokenSets {

  TokenSet LOGICAL_OPERATORS = create(T_LAND, T_LOR);
  TokenSet EQUALITY_OPERATORS = create(T_EQ, T_NEQ, T_ID, T_NID);
  TokenSet RELATIONAL_OPERATORS = create(T_GT, T_GE, T_LT, T_LE, T_COMPARE);
  TokenSet BITWISE_OPERATORS = create(T_BAND, T_BOR, T_XOR);
  TokenSet ADDITIVE_OPERATORS = create(T_PLUS, T_MINUS);
  TokenSet MULTIPLICATIVE_OPERATORS = create(T_STAR, T_DIV, T_REM);
  TokenSet SHIFT_OPERATORS = create(LEFT_SHIFT_SIGN, RIGHT_SHIFT_SIGN, RIGHT_SHIFT_UNSIGNED_SIGN);
  TokenSet REGEX_OPERATORS = create(T_REGEX_FIND, T_REGEX_MATCH);
  TokenSet RANGES = create(T_RANGE, T_RANGE_EX);
  TokenSet OTHER_OPERATORS = create(KW_AS, KW_IN, T_POW, KW_INSTANCEOF);
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

  TokenSet ASSIGNMENT_OPERATORS = create(
    T_ASSIGN,
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
}
