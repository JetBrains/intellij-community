// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy;

import com.intellij.ide.util.projectWizard.importSources.JavaSourceRootDetector;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.NullableFunction;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

public final class GroovySourceRootDetector extends JavaSourceRootDetector {
  @Override
  protected @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getLanguageName() {
    return GroovyBundle.message("file.template.group.title.groovy");
  }

  @Override
  protected @NotNull String getFileExtension() {
    return GroovyFileType.DEFAULT_EXTENSION;
  }

  @Override
  protected @NotNull NullableFunction<CharSequence, String> getPackageNameFetcher() {
    return charSequence -> getPackageName(charSequence);
  }

  public static @Nullable String getPackageName(CharSequence text) {
    Lexer lexer = new GroovyLexer();
    lexer.start(text);
    skipWhitespacesAndComments(lexer);
    final IElementType firstToken = lexer.getTokenType();
    if (firstToken != GroovyTokenTypes.kPACKAGE) {
      return "";
    }
    lexer.advance();
    skipWhitespacesAndComments(lexer);

    final StringBuilder buffer = new StringBuilder();
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

  private static void skipWhitespacesAndComments(Lexer lexer) {
    while(TokenSets.ALL_COMMENT_TOKENS.contains(lexer.getTokenType()) || TokenSets.WHITE_SPACES_SET.contains(lexer.getTokenType())) {
      lexer.advance();
    }
  }
}
