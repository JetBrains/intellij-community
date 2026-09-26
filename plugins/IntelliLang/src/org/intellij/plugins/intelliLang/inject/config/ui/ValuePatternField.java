// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.plugins.intelliLang.inject.config.ui;

import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.ui.EditorTextField;
import com.intellij.ui.LanguageTextField;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Text field for the "Value-Pattern" of an injection. It is highlighted as a regex when RegExp support is installed,
 * and validated by {@link ValuePatternAnnotator}.
 */
@ApiStatus.Internal
public final class ValuePatternField {
  static final Key<Boolean> KEY = Key.create("IS_VALUE_PATTERN");

  private ValuePatternField() {
  }

  public static @NotNull EditorTextField create(@NotNull Project project, @NotNull String pattern) {
    return new LanguageTextField(Language.findLanguageByID("RegExp"), project, pattern, new LanguageTextField.SimpleDocumentCreator() {
      @Override
      public void customizePsiFile(PsiFile psiFile) {
        psiFile.putCopyableUserData(KEY, Boolean.TRUE);
      }
    });
  }
}
