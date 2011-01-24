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
 *         Date: 12/6/10
 *         Time: 10:18 AM
 */
public class HorizontalScrollBarEditorCustomization extends AbstractEditorCustomization {

  public HorizontalScrollBarEditorCustomization() {
    super(Feature.HORIZONTAL_SCROLLBAR);
  }

  @Override
  protected void doProcessCustomization(@NotNull EditorEx editor, @NotNull Feature feature, boolean apply) {
    editor.setHorizontalScrollbarVisible(apply);
  }
}
