/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.ui.EditorCustomization;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;

/**
 * @author nik
 */
public class SpellCheckingEditorCustomizationProvider {
  @NotNull
  public static SpellCheckingEditorCustomizationProvider getInstance() {
    return ServiceManager.getService(SpellCheckingEditorCustomizationProvider.class);
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
