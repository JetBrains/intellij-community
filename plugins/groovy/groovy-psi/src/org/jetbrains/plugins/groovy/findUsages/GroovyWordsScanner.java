// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.lang.cacheBuilder.VersionedWordsScanner;
import com.intellij.lang.cacheBuilder.WordOccurrence;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.STRING_SQ;
import static org.jetbrains.plugins.groovy.lang.psi.GroovyElementTypes.STRING_TSQ;

class GroovyWordsScanner extends VersionedWordsScanner {
  private Lexer myLexer;

  @Override
  public void processWords(@NotNull CharSequence fileText, @NotNull Processor<? super WordOccurrence> processor) {
    if (myLexer == null) {
      myLexer = new GroovyLexer();
    }

    myLexer.start(fileText);
    WordOccurrence occurrence = null; // shared occurrence

    while (myLexer.getTokenType() != null) {
      final IElementType type = myLexer.getTokenType();
      if (type == GroovyTokenTypes.mIDENT || TokenSets.KEYWORDS.contains(type)) {
        if (occurrence == null) occurrence = new WordOccurrence(fileText,myLexer.getTokenStart(),myLexer.getTokenEnd(), WordOccurrence.Kind.CODE);
        else occurrence.init(fileText,myLexer.getTokenStart(),myLexer.getTokenEnd(), WordOccurrence.Kind.CODE);
        if (!processor.process(occurrence)) return;
      }
      else if (TokenSets.COMMENT_SET.contains(type)) {
        if (!stripWords(processor, fileText,myLexer.getTokenStart(),myLexer.getTokenEnd(), WordOccurrence.Kind.COMMENTS, occurrence)) return;
      }
      else if (TokenSets.STRING_LITERALS.contains(type)) {
        if (!stripWords(processor, fileText, myLexer.getTokenStart(), myLexer.getTokenEnd(), WordOccurrence.Kind.LITERALS, occurrence)) {
          return;
        }

        if (type == STRING_SQ || type == STRING_TSQ) {
          if (!stripWords(processor, fileText, myLexer.getTokenStart(),myLexer.getTokenEnd(),WordOccurrence.Kind.CODE, occurrence)) return;
        }
      } else {
        if (!stripWords(processor, fileText, myLexer.getTokenStart(), myLexer.getTokenEnd(), null, occurrence)) {
          return;
        }
      }

      myLexer.advance();
    }
  }

  @Override
  public int getVersion() {
    return 2;
  }

  private static boolean stripWords(final Processor<? super WordOccurrence> processor,
                                    final CharSequence tokenText,
                                    int from,
                                    int to,
                                    final WordOccurrence.Kind kind,
                                    WordOccurrence occurrence) {
    // This code seems strange but it is more effective as Character.isJavaIdentifier_xxx_ is quite costly operation due to unicode
    int index = from;

    ScanWordsLoop:
    while (true) {
      while (true) {
        if (index == to) break ScanWordsLoop;
        char c = tokenText.charAt(index);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') ||
            (Character.isJavaIdentifierStart(c) && c != '$')) {
          break;
        }
        index++;
      }
      int index1 = index;
      while (true) {
        index++;
        if (index == to) break;
        char c = tokenText.charAt(index);
        if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) continue;
        if (!Character.isJavaIdentifierPart(c) || c == '$') break;
      }

      if (occurrence == null) occurrence = new WordOccurrence(tokenText,index1, index, kind);
      else occurrence.init(tokenText,index1, index, kind);
      if (!processor.process(occurrence)) return false;
    }
    return true;
  }
}
