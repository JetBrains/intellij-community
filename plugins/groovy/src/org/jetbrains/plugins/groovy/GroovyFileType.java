package org.jetbrains.plugins.groovy;

import com.intellij.openapi.fileTypes.LanguageFileType;

import javax.swing.*;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * Represents Groovy file properites, such as extension etc.
 *
 * @author Ilya.Sergey
 */
public class GroovyFileType extends LanguageFileType {

  public static final GroovyFileType GROOVY_FILE_TYPE = new GroovyFileType();
  public static final Icon GROOVY_LOGO = Icons.FILE_TYPE;

  private GroovyFileType() {
    super(new GroovyLanguage());
  }

  @NotNull
  @NonNls
  public String getName() {
    return "Groovy";
  }

  @NotNull
  public String getDescription() {
    return "Groovy Scripts and Classes";
  }

  @NotNull
  @NonNls
  public String getDefaultExtension() {
    return "groovy";
  }

  public Icon getIcon() {
    return GROOVY_LOGO;
  }

  public boolean isJVMDebuggingSupported() {
    return true;
  }

}
