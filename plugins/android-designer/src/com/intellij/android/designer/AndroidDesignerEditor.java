/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer;

import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.designer.DesignerEditor;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public final class AndroidDesignerEditor extends DesignerEditor {
  public AndroidDesignerEditor(Project project, VirtualFile file) {
    super(project, file);
  }

  @Override
  @NotNull
  protected DesignerEditorPanel createDesignerPanel(Module module, VirtualFile file) {
    return new AndroidDesignerEditorPanel(module, file);
  }

  @NotNull
  @Override
  public String getName() {
    return AndroidDesignerBundle.message("editor.tab.title");
  }

  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;  // TODO: Auto-generated method stub
  }
}