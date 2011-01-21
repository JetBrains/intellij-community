/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;
import java.util.Set;

/**
 * {@link EditorCustomization} for {@link EditorCustomization.Feature#NO_ADDITIONAL_PAGE_AT_BOTTOM}.
 * 
 * @author Denis Zhdanov
 * @since 1/21/11 4:06 PM
 */
public class AdditionalPageAtBottomEditorCustomization implements EditorCustomization {

  @Override
  public Set<Feature> getSupportedFeatures() {
    return EnumSet.of(Feature.NO_ADDITIONAL_PAGE_AT_BOTTOM);
  }

  @Override
  public void addCustomization(@NotNull EditorEx editor, @NotNull Feature feature) {
    editor.getSettings().setAdditionalPageAtBottom(false);
  }

  @Override
  public void removeCustomization(@NotNull EditorEx editor, @NotNull Feature feature) {
    editor.getSettings().setAdditionalPageAtBottom(true);
  }
}
