// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.lang.Language;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Defines common contract for building {@link EditorTextField} with necessary combinations of features.
 *
 * @author Denis Zhdanov
 */
public interface EditorTextFieldProvider {
  static EditorTextFieldProvider getInstance() {
    return ApplicationManager.getApplication().getService(EditorTextFieldProvider.class);
  }

  /**
   * This factory method allows creation of an editor where the given customizations are applied to the editor.
   *
   * @param language   target language used by document that will be displayed by returned editor
   * @param project    target project
   * @param features
   * @return {@link EditorTextField} with specified customizations applied to its editor.
   */
  @NotNull
  EditorTextField getEditorField(@NotNull Language language, @NotNull  Project project, @NotNull Iterable<? extends EditorCustomization> features);

}
