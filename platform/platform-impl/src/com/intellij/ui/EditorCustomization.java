/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Defines contract for functionality that is able to customize editors.
 * <p/>
 * It's assumed that it works in terms of {@link EditorFeature features} that can be applied to editors, i.e. every
 * customization implementation is assumed to have a corresponding {@link EditorFeature} implementation which
 * enables/disables it and potentially provides some configuration.  This indirection allows us to have customizer
 * extensions provided by other modules (see {@link SpellCheckingEditorFeature} for example)
 *
 * @author Denis Zhdanov
 * @since Aug 20, 2010 4:26:04 PM
 */
public abstract class EditorCustomization {

  public static ExtensionPointName<EditorCustomization> EP_NAME = ExtensionPointName.create("com.intellij.editorCustomization");

  /**
   * Apply the given feature to the given editor.
   *
   * Validates the given {@link EditorFeature} configures this customization, and hence can safely be
   * called on all {@link EditorCustomization} extension points for any {@link EditorFeature}
   *
   * @param editor The editor to customize
   * @param feature The feature configuration
   */
  public void doProcessCustomization(@NotNull EditorEx editor, @NotNull EditorFeature feature) {
    if (!getFeatureClass().isAssignableFrom(feature.getClass())) {
      return;
    }

    customize(editor, feature);
  }

  /**
   * All subclass must declare an {@link EditorFeature} class which configures them.
   *
   * @return The {@link EditorFeature} class which corresponds to this {@link EditorCustomization}
   */
  protected abstract Class<? extends EditorFeature> getFeatureClass();

  /**
   * Subclasses should apply their customizations in this method.  Parameter "feature" is
   * guaranteed by {@link #doProcessCustomization} to match the type returned by {@link #getFeatureClass}
   *
   * @param editor The editor to customize
   * @param feature The feature configuration
   */
  protected abstract void customize(@NotNull EditorEx editor, @NotNull EditorFeature feature);
}
