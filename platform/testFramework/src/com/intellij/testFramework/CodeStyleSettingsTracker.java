// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.application.options.CodeStyle;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Checks that {@link CodeStyleSettings} are not overwritten by a rogue test.
 * Usage: {@code setUp() { tracker = new CodeStyleSettingsTracker(...); } tearDown() { tracker.checkForSettingsDamage(); } }
*/
public class CodeStyleSettingsTracker {
  private final Supplier<? extends CodeStyleSettings> myCurrentSettingsSupplier;
  private CodeStyleSettings myOldSettings;

  public CodeStyleSettingsTracker(@NotNull Supplier<? extends CodeStyleSettings> currentSettingsSupplier) {
    myCurrentSettingsSupplier = currentSettingsSupplier;
    CodeStyleSettings settings = currentSettingsSupplier.get();
    if (settings != null) {
      settings.getIndentOptions(FileTypeManager.getInstance().getStdFileType("JAVA"));
      myOldSettings = CodeStyle.createTestSettings(settings);
    }
  }

  public void checkForSettingsDamage() {
    CodeStyleSettings oldSettings = myOldSettings;
    if (oldSettings == null) {
      return;
    }

    myOldSettings = null;

    UsefulTestCase.doCheckForSettingsDamage(oldSettings, myCurrentSettingsSupplier.get());
  }
}
