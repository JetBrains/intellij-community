/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.codeStyle;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.options.Configurable;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CodeStyleSettingsProvider;
import com.intellij.psi.codeStyle.DisplayPriority;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyLanguage;

public class GroovyGenerationSettingsProvider extends CodeStyleSettingsProvider {
  @Override
  @NotNull
  public Configurable createSettingsPage(final @NotNull CodeStyleSettings settings, final @NotNull CodeStyleSettings originalSettings) {
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
