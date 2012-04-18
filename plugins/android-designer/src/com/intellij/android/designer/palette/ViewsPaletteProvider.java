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
package com.intellij.android.designer.palette;

import com.intellij.android.designer.AndroidDesignerEditorProvider;
import com.intellij.android.designer.model.ViewsMetaManager;
import com.intellij.designer.DesignerToolWindowManager;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.palette.AbstractPaletteProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Alexander Lobas
 */
public class ViewsPaletteProvider extends AbstractPaletteProvider {
  private final Project myProject;

  public ViewsPaletteProvider(Project project) {
    myProject = project;
  }

  @Override
  protected boolean accept(VirtualFile virtualFile) {
    return AndroidDesignerEditorProvider.acceptLayout(myProject, virtualFile) &&
           DesignerToolWindowManager.getInstance(myProject).getActiveDesigner() != null;
  }

  @Override
  protected MetaManager getMetaManager() {
    return ViewsMetaManager.getInstance(myProject);
  }
}