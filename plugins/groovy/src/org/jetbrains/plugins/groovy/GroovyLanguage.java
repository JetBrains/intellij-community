package org.jetbrains.plugins.groovy;

import com.intellij.lang.Language;
import org.jetbrains.annotations.NonNls;

/**
 * @author Ilya.Sergey
 */
public class GroovyLanguage extends Language {
  public GroovyLanguage() {
    super("Groovy");
  }

  protected GroovyLanguage(@NonNls String id) {
    super(id);
  }

  protected GroovyLanguage(@NonNls String ID, @NonNls String... mimeTypes) {
    super(ID, mimeTypes);
  }
}
