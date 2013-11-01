package com.jetbrains.json.formatter;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import com.jetbrains.json.JsonLanguage;

/**
 * @author Mikhail Golubev
 */
public class JsonCodeStyleSettings extends CustomCodeStyleSettings {

  // Standard option has awkward description "Code braces"
  public boolean SPACE_WITHIN_BRACES = false;
  public boolean SPACE_AFTER_COLON = true;
  public boolean SPACE_BEFORE_COLON = false;


  public JsonCodeStyleSettings(CodeStyleSettings container) {
    super(JsonLanguage.INSTANCE.getID(), container);
  }
}
