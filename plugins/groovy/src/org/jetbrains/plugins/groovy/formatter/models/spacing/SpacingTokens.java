// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.plugins.groovy.formatter.models.spacing;

import com.intellij.psi.tree.TokenSet;

import static com.intellij.psi.tree.TokenSet.create;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.*;

/**
 * @author ilyas
 */
public interface SpacingTokens {

  TokenSet LEFT_BRACES = create(T_LPAREN, T_LBRACK, T_LBRACE);
  TokenSet RIGHT_BRACES = create(T_RPAREN, T_RBRACK, T_RBRACE);

  TokenSet POSTFIXES = create(T_DEC, T_INC);
  TokenSet PREFIXES = create(T_DEC, T_INC, T_AT, T_BNOT, T_NOT);
  TokenSet PREFIXES_OPTIONAL = create(T_PLUS, T_MINUS);
}
