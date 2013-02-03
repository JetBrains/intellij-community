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
import org.jetbrains.annotations.NotNull;

/**
 * Editor customization that can make target editor soft wraps-aware.
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since Aug 20, 2010 4:54:48 PM
 */
public class SoftWrapsEditorCustomization extends EditorCustomization {

  @Override
  protected Class<? extends EditorFeature> getFeatureClass() {
    return SoftWrapsEditorFeature.class;
  }

  @Override
  protected void customize(@NotNull EditorEx editor, @NotNull EditorFeature feature) {
    editor.getSettings().setUseSoftWraps(feature.isEnabled());
  }
}
