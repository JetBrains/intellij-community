package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.lang.cacheBuilder.WordOccurrence;
import com.intellij.lang.cacheBuilder.WordsScanner;
import com.intellij.lexer.Lexer;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.Processor;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyLexer;
import org.jetbrains.plugins.groovy.lang.lexer.GroovyTokenTypes;
import org.jetbrains.plugins.groovy.lang.lexer.TokenSets;

/**
 * @author ven
*/
class GroovyWordsScanner implements WordsScanner
{
  private Lexer myLexer;
  public GroovyWordsScanner()
  {
    myLexer = new GroovyLexer();
  }

  public void processWords(CharSequence fileText, Processor<WordOccurrence> processor) {
    myLexer.start(fileText, 0, fileText.length(),0);
    WordOccurrence occurrence = null; // shared occurrence

    while (myLexer.getTokenType() != null) {
      final IElementType type = myLexer.getTokenType();
      if (type == GroovyTokenTypes.mIDENT || TokenSets.KEYWORD_PROPERTY_NAMES.contains(type)) {
        if (occurrence == null) occurrence = new WordOccurrence(fileText,myLexer.getTokenStart(),myLexer.getTokenEnd(), WordOccurrence.Kind.CODE);
        else occurrence.init(fileText,myLexer.getTokenStart(),myLexer.getTokenEnd(), WordOccurrence.Kind.CODE);
        if (!processor.process(occurrence)) return;
      }
      else if (GroovyTokenTypes.COMMENT_SET.contains(type)) {
        if (!stripWords(processor, fileText,myLexer.getTokenStart(),myLexer.getTokenEnd(), WordOccurrence.Kind.COMMENTS, occurrence)) return;
      }
      else if (GroovyTokenTypes.STRING_LITERAL_SET.contains(type)) {
        if (!stripWords(processor, fileText, myLexer.getTokenStart(),myLexer.getTokenEnd(),WordOccurrence.Kind.LITERALS, occurrence)) return;

        if (type == GroovyTokenTypes.mSTRING_LITERAL) {
          if (!stripWords(processor, fileText, myLexer.getTokenStart(),myLexer.getTokenEnd(),WordOccurrence.Kind.CODE, occurrence)) return;
        }
      }

      myLexer.advance();
    }
  }

  private static boolean stripWords(final Processor<WordOccurrence> processor,
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
