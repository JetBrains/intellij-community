/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.formatter;

import com.intellij.application.options.IndentOptionsEditor;
import com.intellij.application.options.SmartIndentOptionsEditor;
import com.intellij.lang.Language;
import com.intellij.psi.codeStyle.CodeStyleSettingsCustomizable;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author Rustam Vishnyakov
 */
public class GroovyLanguageCodeStyleSettingsProvider extends LanguageCodeStyleSettingsProvider {
  @NotNull
  @Override
  public Language getLanguage() {
    return GroovyFileType.GROOVY_LANGUAGE;
  }

  @Override
  public void customizeSettings(@NotNull CodeStyleSettingsCustomizable consumer,
                                @NotNull SettingsType settingsType) {
    consumer.showAllStandardOptions();
  }

  @Override
  public CommonCodeStyleSettings getDefaultCommonSettings() {
    CommonCodeStyleSettings defaultSettings = new CommonCodeStyleSettings(GroovyFileType.GROOVY_LANGUAGE);
    defaultSettings.initIndentOptions();
    return defaultSettings;
  }

  @Override
  public boolean usesSharedPreview() {
    return false;
  }

  @Override
  public String getCodeSample(@NotNull SettingsType settingsType) {
    switch (settingsType) {
      case INDENT_SETTINGS:
        return INDENT_OPTIONS_SAMPLE;
      case SPACING_SETTINGS:
        return SPACING_SAMPLE;
      default:
        return "";
    }
  }

  @Override
  public IndentOptionsEditor getIndentOptionsEditor() {
    return new SmartIndentOptionsEditor();
  }

  private final static String INDENT_OPTIONS_SAMPLE =
    "def foo(int arg) {\n" +
    "  return Math.max(arg,\n" +
    "      0)\n" +
    "}";
  
  private final static String SPACING_SAMPLE =
    "class Foo {\n" +
    "  private int[] list = [1, 3, 5, 6, 7, 87, 1213, 2]\n" +
    "\n" +
    "  public static foo(int x, int y) {\n" +
    "    for (int i = 0; i < x; i++) {\n" +
    "      y += (y ^ 0x123) << 2;\n" +
    "    }\n" +
    "    int j = 0;\n" +
    "    while (j < 10) {\n" +
    "      try {\n" +
    "        if (0 < x && x < 10) {\n" +
    "          while (x != y) {\n" +
    "            x = f(x * 3 + 5);\n" +
    "          }\n" +
    "        } else {\n" +
    "          synchronized (this) {\n" +
    "            switch (e.getCode()) {\n" +
    "            //...\n" +
    "            }\n" +
    "          }\n" +
    "        }\n" +
    "      } catch (MyException e) {\n" +
    "      } finally {\n" +
    "        int[] arr = (int[]) g(y);\n" +
    "        x = y >= 0 ? arr[y] : -1;\n" +
    "      }\n" +
    "    }\n" +
    "  }\n" +
    "\n" +
    "}";
}
