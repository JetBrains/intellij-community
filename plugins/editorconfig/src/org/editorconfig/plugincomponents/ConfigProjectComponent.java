package org.editorconfig.plugincomponents;

import com.intellij.AppTopics;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFileAdapter;
import com.intellij.openapi.vfs.VirtualFileEvent;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.messages.MessageBus;
import org.editorconfig.configmanagement.EditorSettingsManager;
import org.editorconfig.configmanagement.EncodingManager;
import org.editorconfig.configmanagement.LineEndingsManager;
import org.jetbrains.annotations.NotNull;

public class ConfigProjectComponent implements ProjectComponent {

  public ConfigProjectComponent(final Project project, final EditorFactory editorFactory) {
    // Register project-level config managers
    MessageBus bus = project.getMessageBus();
    EditorSettingsManager editorSettingsManager = new EditorSettingsManager(project);
    EncodingManager encodingManager = new EncodingManager(project);
    LineEndingsManager lineEndingsManager = new LineEndingsManager(project);
    bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, encodingManager);
    bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, editorSettingsManager);
    bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, lineEndingsManager);
    VirtualFileManager.getInstance().addVirtualFileListener(new VirtualFileAdapter() {
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
        if (".editorconfig".equals(event.getFile().getName())) {
          for (Editor editor : editorFactory.getAllEditors()) {
            ((EditorEx)editor).reinitSettings();
          }
        }
      }
    }, project);
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "ConfigProjectComponent";
  }

  public void projectOpened() {}

  public void projectClosed() {}
}
