package org.jetbrains.android.formatter;

import com.intellij.application.options.CodeStyleAbstractConfigurable;
import com.intellij.application.options.CodeStyleAbstractPanel;
import com.intellij.lang.Language;
import com.intellij.lang.xml.XMLLanguage;
import com.intellij.openapi.options.Configurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.CustomCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidXmlCodeStyleSettingsProvider extends CodeStyleSettingsProvider {
  @NotNull
  @Override
  public Configurable createSettingsPage(CodeStyleSettings settings, CodeStyleSettings originalSettings) {
    return new CodeStyleAbstractConfigurable(settings, originalSettings, getConfigurableDisplayName()){
      protected CodeStyleAbstractPanel createPanel(final CodeStyleSettings settings) {
        return new AndroidXmlCodeStylePanel(getCurrentSettings(), settings);
      }

      public String getHelpTopic() {
        return null;
      }
    };
  }

  @Override
  public String getConfigurableDisplayName() {
    return "Android";
  }

  @Override
  public boolean hasSettingsPage() {
    return false;
  }

  @Nullable
  @Override
  public Language getLanguage() {
    return XMLLanguage.INSTANCE;
  }

  @Override
  public CustomCodeStyleSettings createCustomSettings(CodeStyleSettings settings) {
    return new AndroidXmlCodeStyleSettings(settings);
  }
}
