// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.plugincomponents;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import org.editorconfig.EditorConfigRegistry;
import org.editorconfig.configmanagement.EditorSettingsManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;

final class EditorConfigModificationListener implements BulkFileListener {
  @Override
  public void after(@NotNull List<? extends VFileEvent> events) {
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      return;
    }

    for (VFileEvent event : events) {
      VirtualFile file = event.getFile();
      if (file == null || !file.getName().equals(".editorconfig")) {
        continue;
      }

      for (Project project : ProjectManager.getInstance().getOpenProjects()) {
        if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(file) ||
            !EditorConfigRegistry.shouldStopAtProjectRoot()) {
          ApplicationManager.getApplication().invokeLater(() -> {
            SettingsProviderComponent.getInstance().incModificationCount();
            for (Editor editor : EditorFactory.getInstance().getAllEditors()) {
              if (editor.isDisposed()) continue;
              EditorSettingsManager.applyEditorSettings(editor);
              ((EditorEx)editor).reinitSettings();
            }
          });
        }
      }
    }
  }
}
