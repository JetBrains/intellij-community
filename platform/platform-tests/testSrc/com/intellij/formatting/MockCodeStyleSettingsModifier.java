// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting;

import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier;
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor;
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

public class MockCodeStyleSettingsModifier implements CodeStyleSettingsModifier {
  private final String                      myFileName;
  private final Consumer<CodeStyleSettings> mySettingsConsumer;

  public MockCodeStyleSettingsModifier(@NotNull String fileName, @NotNull Consumer<CodeStyleSettings> consumer) {
    myFileName = fileName;
    mySettingsConsumer = consumer;
  }

  @Override
  public boolean modifySettings(@NotNull TransientCodeStyleSettings settings,
                                @NotNull PsiFile file) {
    if (myFileName.equals(file.getName())) {
      mySettingsConsumer.accept(settings);
      return true;
    }
    return false;
  }

  @Override
  public String getName() {
    return "Mock Code Style Modifier";
  }

  @Nullable
  @Override
  public CodeStyleStatusBarUIContributor getStatusBarUiContributor(@NotNull TransientCodeStyleSettings transientSettings) {
    return null;
  }
}
