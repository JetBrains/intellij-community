// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.formatter;

import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import org.jetbrains.kotlin.idea.KotlinLanguage;
import org.jetbrains.kotlin.idea.core.formatter.KotlinCodeStyleSettings;
import org.jetbrains.kotlin.test.SettingsConfigurator;

public final class FormatSettingsUtil {
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
