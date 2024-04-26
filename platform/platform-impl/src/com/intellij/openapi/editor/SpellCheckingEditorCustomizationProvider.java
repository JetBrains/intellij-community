// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.EditorCustomization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

public class SpellCheckingEditorCustomizationProvider {
  public static @NotNull SpellCheckingEditorCustomizationProvider getInstance() {
    return ApplicationManager.getApplication().getService(SpellCheckingEditorCustomizationProvider.class);
  }

  public final @Nullable EditorCustomization getCustomization(boolean enabled) {
    return enabled ? getEnabledCustomization() : getDisabledCustomization();
  }

  public @Nullable EditorCustomization getEnabledCustomization() {
    return null;
  }

  public @Nullable EditorCustomization getDisabledCustomization() {
    return null;
  }

  /**
   * @return set containing {@link com.intellij.codeInspection.InspectionProfileEntry#getShortName()} values for spell checking inspections
   */
  public Set<String> getSpellCheckingToolNames() {
    return Collections.emptySet();
  }
}
