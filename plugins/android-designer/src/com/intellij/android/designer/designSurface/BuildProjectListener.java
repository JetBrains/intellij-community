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
package com.intellij.android.designer.designSurface;

import com.intellij.designer.DesignerEditor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerManager;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author Alexander Lobas
 */
public class BuildProjectListener implements ProjectComponent {
  private final FileEditorManager myFileEditorManager;

  public BuildProjectListener(Project project, FileEditorManager fileEditorManager) {
    myFileEditorManager = fileEditorManager;
    CompilerManager.getInstance(project).addAfterTask(new CompileTask() {
      @Override
      public boolean execute(CompileContext context) {
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            updateDesigners();
          }
        });
        return true;
      }
    });
  }

  private void updateDesigners() {
    for (FileEditor editor : myFileEditorManager.getAllEditors()) {
      if (editor instanceof DesignerEditor) {
        DesignerEditor designerEditor = (DesignerEditor)editor;
        if (designerEditor.getDesignerPanel() instanceof AndroidDesignerEditorPanel) {
          AndroidDesignerEditorPanel panel = (AndroidDesignerEditorPanel)designerEditor.getDesignerPanel();
          panel.buildProject();
        }
      }
    }
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @NotNull
  @Override
  public String getComponentName() {
    return "BuildProjectListener";
  }
}