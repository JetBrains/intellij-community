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
 * @author irengrig
 */
public class HorizontalScrollBarEditorCustomization extends SimpleEditorCustomization {

  public static final HorizontalScrollBarEditorCustomization ENABLED = new HorizontalScrollBarEditorCustomization(true);
  public static final HorizontalScrollBarEditorCustomization DISABLED = new HorizontalScrollBarEditorCustomization(false);

  private HorizontalScrollBarEditorCustomization(boolean enabled) {
    super(enabled);
  }

  @Override
  public void customize(@NotNull EditorEx editor) {
    editor.setHorizontalScrollbarVisible(isEnabled());
  }
}
