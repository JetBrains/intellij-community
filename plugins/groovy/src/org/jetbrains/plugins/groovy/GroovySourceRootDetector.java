/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy;

import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetector;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.NullableFunction;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

/**
 * @author nik
 */
public class GroovySourceRootDetector extends JavaSourceRootDetector {
  @NotNull
  @Override
  protected String getLanguageName() {
    return "Groovy";
  }

  @NotNull
  @Override
  protected String getFileExtension() {
    return GroovyFileType.DEFAULT_EXTENSION;
  }

  @Override
  @NotNull
  protected NullableFunction<CharSequence, String> getPackageNameFetcher() {
    return new NullableFunction<CharSequence, String>() {
      @Override
      public String fun(CharSequence charSequence) {
        return getPackageName(charSequence);
      }
    };
  }

  @Nullable
  public static String getPackageName(CharSequence text) {
    Lexer lexer = new GroovyLexer();
    lexer.start(text);
    skipWhitespacesAndComments(lexer);
    final IElementType firstToken = lexer.getTokenType();
    if (firstToken != GroovyTokenTypes.kPACKAGE) {
      return "";
    }
    lexer.advance();
    skipWhitespacesAndComments(lexer);

    final StringBuilder buffer = StringBuilderSpinAllocator.alloc();
    try {
      while(true){
        if (lexer.getTokenType() != GroovyTokenTypes.mIDENT) break;
        buffer.append(text, lexer.getTokenStart(), lexer.getTokenEnd());
        lexer.advance();
        skipWhitespacesAndComments(lexer);
        if (lexer.getTokenType() != GroovyTokenTypes.mDOT) break;
        buffer.append('.');
        lexer.advance();
        skipWhitespacesAndComments(lexer);
      }
      String packageName = buffer.toString();
      if (packageName.isEmpty() || StringUtil.endsWithChar(packageName, '.')) return null;
      return packageName;
    }
    finally {
      StringBuilderSpinAllocator.dispose(buffer);
    }
  }

  private static void skipWhitespacesAndComments(Lexer lexer) {
    while(TokenSets.ALL_COMMENT_TOKENS.contains(lexer.getTokenType()) || TokenSets.WHITE_SPACES_SET.contains(lexer.getTokenType())) {
      lexer.advance();
    }
  }
}
