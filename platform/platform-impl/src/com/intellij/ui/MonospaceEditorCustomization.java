/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.ex.EditorEx;
import org.jetbrains.annotations.NotNull;

public class MonospaceEditorCustomization implements EditorCustomization {
  private static final EditorCustomization INSTANCE = new MonospaceEditorCustomization();

  @NotNull
  public static EditorCustomization getInstance() {
    return INSTANCE;
  }

  @Override
  public void customize(@NotNull EditorEx editor) {
    /* For the sake of simplicity and consistency, we load the global editor scheme here, although its font is not necessarily monospace.
       However if the main editor has not monospaced font, we don't wanna use monospace here either. */
    editor.setColorsScheme(EditorColorsManager.getInstance().getGlobalScheme());
  }
}
