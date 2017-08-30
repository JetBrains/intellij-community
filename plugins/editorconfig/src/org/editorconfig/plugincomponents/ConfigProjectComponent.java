package org.editorconfig.plugincomponents;

import com.intellij.AppTopics;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.startup.StartupActivity;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.messages.MessageBus;
import org.editorconfig.configmanagement.EditorSettingsManager;
import org.editorconfig.configmanagement.EncodingManager;
import org.editorconfig.configmanagement.LineEndingsManager;
import org.jetbrains.annotations.NotNull;

public class ConfigProjectComponent implements StartupActivity, DumbAware {
  @Override
  public void runActivity(@NotNull Project project) {
    if (ApplicationManager.getApplication().isUnitTestMode()) return;

    // Register project-level config managers
    final EditorFactory editorFactory = EditorFactory.getInstance();
    MessageBus bus = project.getMessageBus();
    EditorSettingsManager editorSettingsManager = new EditorSettingsManager(project);
    EncodingManager encodingManager = new EncodingManager(project);
    LineEndingsManager lineEndingsManager = new LineEndingsManager(project);
    bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, encodingManager);
    bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, editorSettingsManager);
    bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, lineEndingsManager);
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileListener() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent event) {
        updateOpenEditors(event);
      }

      @Override
      public void fileDeleted(@NotNull VirtualFileEvent event) {
        updateOpenEditors(event);
      }

      @Override
      public void contentsChanged(@NotNull VirtualFileEvent event) {
        updateOpenEditors(event);
      }

      private void updateOpenEditors(VirtualFileEvent event) {
        final VirtualFile file = event.getFile();
        if (".editorconfig".equals(file.getName())) {
          if (ProjectRootManager.getInstance(project).getFileIndex().isInContent(file) ||
              !Registry.is("editor.config.stop.at.project.root")) {
            SettingsProviderComponent.getInstance().incModificationCount();
            for (Editor editor : editorFactory.getAllEditors()) {
              if (editor.isDisposed()) continue;
              ((EditorEx)editor).reinitSettings();
            }
          }
        }
      }
    }, project);
  }
}
