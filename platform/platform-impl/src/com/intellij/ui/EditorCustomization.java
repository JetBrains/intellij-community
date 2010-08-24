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

import java.util.Set;

/**
 * Defines contract for functionality that is able to customize editors.
 * <p/>
 * It's assumed that it works in terms of {@link Feature features} that can be applied to editors, i.e. every
 * customization implementation is assumed to be able to provide support for <code>[1; *]</code> features.
 *
 * @author Denis Zhdanov
 * @since Aug 20, 2010 4:26:04 PM
 */
public interface EditorCustomization {

  enum Feature {
    SOFT_WRAP, SPELL_CHECK
  }

  ExtensionPointName<EditorCustomization> EP_NAME = ExtensionPointName.create("com.intellij.editorCustomization");

  /**
   * @return    set of editor customization features supported by the current class
   */
  Set<Feature> getSupportedFeatures();

  /**
   * Asks to perform customization of the given editor for the given feature.
   *
   * @param editor      editor to customize
   * @param feature     feature to apply to the given editor
   */
  void customize(@NotNull EditorEx editor, @NotNull Feature feature);
}
