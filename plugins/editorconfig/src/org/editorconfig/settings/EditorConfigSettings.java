package org.editorconfig.settings;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;

/**
 * @author Dennis.Ushakov
 */
public class EditorConfigSettings extends CustomCodeStyleSettings {
  public boolean ENABLED = true;

  protected EditorConfigSettings(CodeStyleSettings container) {
    super("editorconfig", container);
  }
}
