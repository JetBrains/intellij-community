// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.util.parser

import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.buildTokenList
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.areBracesBalancedInside
import com.intellij.platform.syntax.util.parser.SyntaxBuilderUtil.isBalancedBlock
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SyntaxBuilderUtilBraceBalanceTest {
  companion object {
    private val L_CURLY = SyntaxElementType("{")
    private val R_CURLY = SyntaxElementType("}")
    private val L_BRACKET = SyntaxElementType("[")
    private val R_BRACKET = SyntaxElementType("]")
    private val OTHER = SyntaxElementType("OTHER")
  }

  // ===== isBalancedBlock =====

  @Test
  fun properBalance_simpleObject() {
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("x", OTHER)
      token("}", R_CURLY)
    }
    assertTrue(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun properBalance_emptyBraces() {
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("}", R_CURLY)
    }
    assertTrue(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun properBalance_nested() {
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("{", L_CURLY)
      token("}", R_CURLY)
      token("}", R_CURLY)
    }
    assertTrue(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun properBalance_emptyTokenList() {
    val tokens = buildTokenList {}
    assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun properBalance_noLeftBrace() {
    val tokens = buildTokenList {
      token("x", OTHER)
      token("}", R_CURLY)
    }
    assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun properBalance_missingRightBrace() {
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("x", OTHER)
    }
    assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun properBalance_extraRight() {
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("}", R_CURLY)
      token("}", R_CURLY)
    }
    assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun properBalance_closingBraceNotLast() {
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("}", R_CURLY)
      token("x", OTHER)
    }
    assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun properBalance_wrongBraceKind() {
    // First token is [ but we check { }
    val tokens = buildTokenList {
      token("[", L_BRACKET)
      token("]", R_BRACKET)
    }
    assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun properBalance_unbalancedNested() {
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("{", L_CURLY)
      token("}", R_CURLY)
    }
    assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun properBalance_ignoredOtherBraces() {
    // isBalancedBlock only looks at the specified brace kind
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("[", L_BRACKET)
      token("}", R_CURLY)
    }
    assertTrue(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun properBalance_singleLeftBrace() {
    val tokens = buildTokenList {
      token("{", L_CURLY)
    }
    assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun properBalance_twoConsecutivePairs() {
    // { } { } — closing brace of the first pair isn't the last token
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("}", R_CURLY)
      token("{", L_CURLY)
      token("}", R_CURLY)
    }
    assertFalse(isBalancedBlock(tokens, L_CURLY, R_CURLY, null))
  }

  // ===== areBracesBalancedInside =====

  @Test
  fun nonNegative_emptyTokenList() {
    val tokens = buildTokenList {}
    assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun nonNegative_noBraces() {
    val tokens = buildTokenList {
      token("x", OTHER)
      token("y", OTHER)
    }
    assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun nonNegative_balancedPair() {
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("x", OTHER)
      token("}", R_CURLY)
    }
    assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun nonNegative_twoConsecutivePairs() {
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("}", R_CURLY)
      token("{", L_CURLY)
      token("}", R_CURLY)
    }
    assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun nonNegative_nestedBalanced() {
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("{", L_CURLY)
      token("}", R_CURLY)
      token("}", R_CURLY)
    }
    assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun nonNegative_rightBeforeLeft() {
    // } { — balance goes negative
    val tokens = buildTokenList {
      token("}", R_CURLY)
      token("{", L_CURLY)
    }
    assertFalse(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun nonNegative_extraRight() {
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("}", R_CURLY)
      token("}", R_CURLY)
    }
    assertFalse(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun nonNegative_extraLeft() {
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("{", L_CURLY)
      token("}", R_CURLY)
    }
    assertFalse(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun nonNegative_singleRight() {
    val tokens = buildTokenList {
      token("}", R_CURLY)
    }
    assertFalse(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun nonNegative_singleLeft() {
    val tokens = buildTokenList {
      token("{", L_CURLY)
    }
    assertFalse(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun nonNegative_ignoredOtherBraces() {
    // Checking { } balance while [ ] is unbalanced — should pass
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("[", L_BRACKET)
      token("}", R_CURLY)
    }
    assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun nonNegative_doesNotRequireFirstToken() {
    // First token is not a brace — that's fine for nonNegative
    val tokens = buildTokenList {
      token("x", OTHER)
      token("{", L_CURLY)
      token("}", R_CURLY)
    }
    assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun nonNegative_doesNotRequireLastToken() {
    // Last token is not a brace — fine for nonNegative
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("}", R_CURLY)
      token("x", OTHER)
    }
    assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun nonNegative_goesNegativeMidStream() {
    // { } } { — balance goes 1, 0, -1 at third token
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("}", R_CURLY)
      token("}", R_CURLY)
      token("{", L_CURLY)
    }
    assertFalse(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  // ===== Cross-brace scenarios (typical JSON use case) =====

  @Test
  fun nonNegative_bracketBalanceInsideObject() {
    // Typical: checking [ ] balance inside { ... [ ... ] ... }
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("[", L_BRACKET)
      token("x", OTHER)
      token("]", R_BRACKET)
      token("}", R_CURLY)
    }
    assertTrue(areBracesBalancedInside(tokens, L_BRACKET, R_BRACKET, null))
  }

  @Test
  fun nonNegative_missingRBracketInsideObject() {
    // { [ x } — brackets unbalanced
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("[", L_BRACKET)
      token("x", OTHER)
      token("}", R_CURLY)
    }
    assertFalse(areBracesBalancedInside(tokens, L_BRACKET, R_BRACKET, null))
  }

  @Test
  fun nonNegative_extraRBracketInsideObject() {
    // { ] x } — bracket balance goes negative
    val tokens = buildTokenList {
      token("{", L_CURLY)
      token("]", R_BRACKET)
      token("x", OTHER)
      token("}", R_CURLY)
    }
    assertFalse(areBracesBalancedInside(tokens, L_BRACKET, R_BRACKET, null))
  }

  @Test
  fun nonNegative_braceBalanceInsideArray() {
    // Typical: checking { } balance inside [ ... { ... } ... ]
    val tokens = buildTokenList {
      token("[", L_BRACKET)
      token("{", L_CURLY)
      token("x", OTHER)
      token("}", R_CURLY)
      token("]", R_BRACKET)
    }
    assertTrue(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }

  @Test
  fun nonNegative_extraRBraceInsideArray() {
    // [ } x ] — brace balance goes negative
    val tokens = buildTokenList {
      token("[", L_BRACKET)
      token("}", R_CURLY)
      token("x", OTHER)
      token("]", R_BRACKET)
    }
    assertFalse(areBracesBalancedInside(tokens, L_CURLY, R_CURLY, null))
  }
}
