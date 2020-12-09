// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.EditorCustomization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

public class SpellCheckingEditorCustomizationProvider {
  @NotNull
  public static SpellCheckingEditorCustomizationProvider getInstance() {
    return ApplicationManager.getApplication().getService(SpellCheckingEditorCustomizationProvider.class);
  }

  @Nullable
  public final EditorCustomization getCustomization(boolean enabled) {
    return enabled ? getEnabledCustomization() : getDisabledCustomization();
  }

  @Nullable
  public EditorCustomization getEnabledCustomization() {
    return null;
  }

  @Nullable
  public EditorCustomization getDisabledCustomization() {
    return null;
  }

  /**
   * @return set containing {@link com.intellij.codeInspection.InspectionProfileEntry#getShortName()} values for spell checking inspections
   */
  public Set<String> getSpellCheckingToolNames() {
    return Collections.emptySet();
  }
}
