/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.externalSystem.service.project.settings.CodeStyleConfigurationImporter;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

import java.util.Map;

public class GroovyCodeStyleImporter implements CodeStyleConfigurationImporter<GroovyCodeStyleSettings> {
  @Override
  public void processSettings(@NotNull GroovyCodeStyleSettings settings, @NotNull Map config) {
    asInt(config.get("CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND"), (it) -> settings.CLASS_COUNT_TO_USE_IMPORT_ON_DEMAND = it);
    asBoolean(config.get("ALIGN_NAMED_ARGS_IN_MAP"), (it) -> settings.ALIGN_NAMED_ARGS_IN_MAP = it);
  }

  @Override
  public boolean canImport(@NotNull String langName) {
    return "groovy".equals(langName);
  }

  @NotNull
  @Override
  public Class<GroovyCodeStyleSettings> getCustomClass() {
    return GroovyCodeStyleSettings.class;
  }

  @NotNull
  @Override
  public Language getLanguage() {
    return GroovyLanguage.INSTANCE;
  }

  private void asInt(Object value, Consumer<Integer> consumer) {
    if (value instanceof Number) {
      consumer.consume(((Number)value).intValue());
    }
  }

  private void asBoolean(Object value, Consumer<Boolean> consumer) {
    if (value instanceof Boolean) {
      consumer.consume((Boolean)value);
    }
  }
}
