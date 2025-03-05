// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.DisplayPriority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

public final class GroovyGenerationSettingsProvider extends CodeStyleSettingsProvider {
  @Override
  public @NotNull Configurable createSettingsPage(final @NotNull CodeStyleSettings settings, final @NotNull CodeStyleSettings originalSettings) {
    return new GroovyCodeStyleGenerationConfigurable(settings);
  }

  @Override
  public String getConfigurableDisplayName() {
    return ApplicationBundle.message("title.code.generation");
  }

  @Override
  public DisplayPriority getPriority() {
    return DisplayPriority.CODE_SETTINGS;
  }

  @Override
  public boolean hasSettingsPage() {
    return false;
  }

  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }
}
