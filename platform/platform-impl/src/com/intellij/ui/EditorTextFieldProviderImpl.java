// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.EditorSettings;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Provides default implementation for {@link EditorTextFieldProvider} service and applies available
 * {@link EditorCustomization customizations} if necessary.
 */
public class EditorTextFieldProviderImpl implements EditorTextFieldProvider {
  @Override
  public @NotNull EditorTextField getEditorField(@NotNull Language language, @NotNull Project project,
                                                 final @NotNull Iterable<? extends EditorCustomization> features) {
    return new MyEditorTextField(language, project, features);
  }

  private static final class MyEditorTextField extends LanguageTextField {

    private final @NotNull Iterable<? extends EditorCustomization> myCustomizations;

    MyEditorTextField(@NotNull Language language, @NotNull Project project, @NotNull Iterable<? extends EditorCustomization> customizations) {
      super(language, project, "", false);
      myCustomizations = customizations;
    }

    @Override
    protected @NotNull EditorEx createEditor() {
      final EditorEx ex = super.createEditor();
      ex.getScrollPane().setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
      ex.setHorizontalScrollbarVisible(true);
      applyDefaultSettings(ex);
      applyCustomizations(ex);
      return ex;
    }

    private static void applyDefaultSettings(EditorEx ex) {
      EditorSettings settings = ex.getSettings();
      settings.setAdditionalColumnsCount(3);
      settings.setVirtualSpace(false);
    }

    private void applyCustomizations(@NotNull EditorEx editor) {
      for (EditorCustomization customization : myCustomizations) {
        customization.customize(editor);
      }
      updateBorder(editor);
    }
  }
}
