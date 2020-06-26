// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.configmanagement.lexer;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.MergeFunction;
import com.intellij.lexer.MergingLexerAdapter;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.PlatformUtils;
import org.editorconfig.language.lexer.EditorConfigLexerAdapter;
import org.editorconfig.language.psi.EditorConfigElementTypes;
import org.jetbrains.annotations.NotNull;

public class EditorConfigLexerFactory {
  @NotNull
  public static Lexer getAdapter() {
    return PlatformUtils.isRider()
           ? new EditorConfigLexerAdapter()
           : new MyLexerAdapter(new IntellijEditorConfigLexerAdapter());
  }

  private static final class MyLexerAdapter extends MergingLexerAdapter {
    private final static TokenSet IDENTIFIER_TOKENS = TokenSet.create(
      EditorConfigElementTypes.IDENTIFIER,
      IntellijEditorConfigTokenTypes.VALUE_CHAR
    );

    private MyLexerAdapter(Lexer original) {
      super(original, IDENTIFIER_TOKENS);
    }

    @Override
    public MergeFunction getMergeFunction() {
      return new MergeFunction() {
        @Override
        public IElementType merge(IElementType type, Lexer originalLexer) {
          if (!IDENTIFIER_TOKENS.contains(type)) {
            return type;
          }

          while (true) {
            final IElementType tokenType = originalLexer.getTokenType();
            if (!IDENTIFIER_TOKENS.contains(tokenType)) break;
            originalLexer.advance();
          }
          return EditorConfigElementTypes.IDENTIFIER;
        }
      };
    }
  }
}
