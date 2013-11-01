package com.jetbrains.json.formatter;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import com.jetbrains.json.JsonLanguage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.SPACES_OTHER;
import static com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable.SPACES_WITHIN;

/**
 * @author Mikhail Golubev
 */
public class JsonLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  private static final String SAMPLE = "{\n" +
                                       "    \"json literals are\": {\n" +
                                       "        \"strings\": [\"foo\", \"bar\", \"\\u0062\\u0061\\u0072\"],\n" +
                                       "        \"numbers\": [42, 6.62606975e-34],\n" +
                                       "        \"boolean values\": [true, false],\n" +
                                       "        \"and null\": null\n" +
                                       "    }\n" +
                                       "}";


  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer, @NotNull SettingsType settingsType) {
    if (settingsType == SettingsType.SPACING_SETTINGS) {
      consumer.showStandardOptions("SPACE_WITHIN_BRACKETS",
                                   "SPACE_AFTER_COMMA",
                                   "SPACE_BEFORE_COMMA");
      consumer.showCustomOption(JsonCodeStyleSettings.class, "SPACE_WITHIN_BRACES", "Braces", SPACES_WITHIN);
      consumer.showCustomOption(JsonCodeStyleSettings.class, "SPACE_BEFORE_COLON", "Before ':'", SPACES_OTHER);
      consumer.showCustomOption(JsonCodeStyleSettings.class, "SPACE_AFTER_COLON", "After ':'", SPACES_OTHER);
    }
    super.customizeSettings(consumer, settingsType);
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return JsonLanguage.INSTANCE;
  }

  @Nullable
  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    // TODO: read sources of this one carefully
    return new SmartIndentOptionsEditor();
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    return SAMPLE;
  }
}
