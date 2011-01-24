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

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Base super class for {@link EditorCustomization editor customizations} that provide the following:
 * <pre>
 * <ul>
 *   <li>
 *     Don't process {@link #addCustomization(EditorEx, Feature)} and {@link #removeCustomization(EditorEx, Feature)} if given feature
 *     is not supported by the current customization (supported features are defined at constructor);
 *   </li>
 * </ul>
 * </pre>
 * 
 * @author Denis Zhdanov
 * @since 1/24/11 3:56 PM
 */
public abstract class AbstractEditorCustomization implements EditorCustomization {
  
  private final Set<Feature> myFeatures = EnumSet.noneOf(Feature.class);

  protected AbstractEditorCustomization(@NotNull Feature... features) {
    myFeatures.addAll(Arrays.asList(features));
  }

  @Override
  public Set<Feature> getSupportedFeatures() {
    return myFeatures;
  }

  @Override
  public void addCustomization(@NotNull EditorEx editor, @NotNull Feature feature) {
    if (!myFeatures.contains(feature)) {
      return;
    }
    doProcessCustomization(editor, feature, true);
  }
  
  @Override
  public void removeCustomization(@NotNull EditorEx editor, @NotNull Feature feature) {
    if (!myFeatures.contains(feature)) {
      return;
    }
    doProcessCustomization(editor, feature, false);
  }

  /**
   * Template method for sub-classes to process target feature applying/removal and being sure that given feature
   * is supported by the current customization.
   *
   * @param editor      target editor to apply the given feature
   * @param feature     target feature to apply to the given editor
   * @param apply       flag the identifies if given feature should be applied/removed from the given editor
   */
  protected abstract void doProcessCustomization(@NotNull EditorEx editor, @NotNull Feature feature, boolean apply);
}
