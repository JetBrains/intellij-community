// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.inspections

import org.jetbrains.idea.devkit.inspections.quickfix.LightDevKitInspectionFixTestBase

abstract class TokenSetInParserDefinitionInspectionTestBase : LightDevKitInspectionFixTestBase() {

  override fun setUp() {
    super.setUp()

    myFixture.addClass("""
      package com.intellij.lang;
      public abstract class Language {}
      """.trimIndent())

    myFixture.addClass("""
      package com.intellij.psi.tree;
      
      import com.intellij.lang.Language;
      
      public class IElementType {
        public IElementType(String debugName, Language language) {
          // any
        }
      }
      """.trimIndent())

    myFixture.addClass("""
      package com.intellij.psi;
      
      import com.intellij.psi.tree.IElementType;
      
      public interface TokenType {
        IElementType WHITE_SPACE = null;
      }
      """.trimIndent())

    myFixture.addClass("""
      package com.intellij.psi.tree;
      
      public final class TokenSet {
        public static final TokenSet EMPTY = null;
        public static final TokenSet ANY = null;
        public static final TokenSet WHITE_SPACE = null;
      
        public static TokenSet create(IElementType... types) {
          return null;
        }
      
        public static TokenSet orSet(TokenSet... sets) {
          return null;
        }
      
      }
      """.trimIndent())

    myFixture.addClass("""
      package com.intellij.lang;
      
      import com.intellij.psi.tree.TokenSet;
      
      public interface ParserDefinition {
        // only a single TokenSet-related method for simplicity
        TokenSet getCommentTokens();
      }
      """.trimIndent())

    myFixture.enableInspections(TokenSetInParserDefinitionInspection())
  }

}
