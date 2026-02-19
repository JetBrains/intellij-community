// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.application.options.TabbedLanguageCodeStylePanel;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import org.jetbrains.plugins.groovy.GroovyLanguage;

/**
 * @author Max Medvedev
 */
public class GroovyCodeStyleMainPanel extends TabbedLanguageCodeStylePanel {
  public GroovyCodeStyleMainPanel(CodeStyleSettings currentSettings, CodeStyleSettings settings) {
    super(GroovyLanguage.INSTANCE, currentSettings, settings);
  }

  @Override
  protected void initTabs(CodeStyleSettings settings) {
    super.initTabs(settings);
    addTab(new GrCodeStyleGroovydocPanel(settings));
    addTab(new GrCodeStyleImportsPanelWrapper(settings));
    for (CodeStyleSettingsProvider provider : CodeStyleSettingsProvider.EXTENSION_POINT_NAME.getExtensionList()) {
      if (provider.getLanguage() == GroovyLanguage.INSTANCE && !provider.hasSettingsPage()) {
        createTab(provider);
      }
    }
  }
}
