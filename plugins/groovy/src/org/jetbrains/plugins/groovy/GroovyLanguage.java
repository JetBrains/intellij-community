package org.jetbrains.plugins.groovy;

import com.intellij.lang.Language;
import com.intellij.lang.ParserDefinition;
import com.intellij.lang.Commenter;
import com.intellij.lang.PsiParser;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyParserDefinition;
import org.jetbrains.plugins.groovy.highlighter.GroovyCommenter;

/**
 * @author Ilya.Sergey
 */
public class GroovyLanguage extends Language {
  public GroovyLanguage() {
    super("Groovy");
  }

  public ParserDefinition getParserDefinition() {
    return new GroovyParserDefinition();
  }

  @Nullable
  public Commenter getCommenter() {
    return new GroovyCommenter();
  }
}
