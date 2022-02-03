// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.formatter;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings;
import org.jetbrains.kotlin.test.SettingsConfigurator;

public class FormatSettingsUtil {
    private FormatSettingsUtil() {
    }

    public static SettingsConfigurator createConfigurator(String fileText, CodeStyleSettings settings) {
        CommonCodeStyleSettings commonSettings = settings.getCommonSettings(KotlinLanguage.INSTANCE);
        return new SettingsConfigurator(fileText,
                                        settings.getCustomSettings(KotlinCodeStyleSettings.class),
                                        commonSettings,
                                        commonSettings.getIndentOptions());
    }
}
