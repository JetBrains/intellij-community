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
package com.jetbrains.reactiveidea;

import com.intellij.codeInsight.highlighting.HighlightManagerImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public class HighlightingManager extends HighlightManagerImpl {
  public HighlightingManager(Project project) {
    super(project);
  }

  @NotNull
  @Override
  protected MarkupModel getMarkupModel(@NotNull Editor editor) {
    return DocumentMarkupModel.forDocument(editor.getDocument(), editor.getProject(), true);
  }
}
