// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Checks that {@link CodeStyleSettings} are not overwritten by a rogue test.
 * Usage: {@code setUp() { tracker = new CodeStyleSettingsTracker(...); } tearDown() { tracker.checkForSettingsDamage(); } }
*/
public class CodeStyleSettingsTracker {
  private final Supplier<CodeStyleSettings> myCurrentSettingsSupplier;
  private CodeStyleSettings myOldSettings;

  public CodeStyleSettingsTracker(@NotNull Supplier<CodeStyleSettings> currentSettingsSupplier) {
    myCurrentSettingsSupplier = currentSettingsSupplier;
    CodeStyleSettings settings = currentSettingsSupplier.get();
    if (settings != null) {
      settings.getIndentOptions(StdFileTypes.JAVA);
      myOldSettings = settings.clone();
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
