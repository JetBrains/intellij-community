package org.editorconfig.plugincomponents;

import com.intellij.AppTopics;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.util.messages.MessageBus;
import org.editorconfig.configmanagement.CodeStyleManager;
import org.editorconfig.configmanagement.EditorSettingsManager;
import org.editorconfig.configmanagement.EncodingManager;
import org.editorconfig.configmanagement.LineEndingsManager;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public class ConfigProjectComponent implements ProjectComponent {
  private final Project project;
  private final CodeStyleManager codeStyleManager;

  public ConfigProjectComponent(Project project) {
    this.project = project;

    // Register project-level config managers
    MessageBus bus = project.getMessageBus();
    codeStyleManager = new CodeStyleManager(project);
    EditorSettingsManager editorSettingsManager = new EditorSettingsManager();
    EncodingManager encodingManager = new EncodingManager(project);
    LineEndingsManager lineEndingsManager = new LineEndingsManager(project);
    bus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, codeStyleManager);
    bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, encodingManager);
    bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, editorSettingsManager);
    bus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, lineEndingsManager);
  }

  public void initComponent() {
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "ConfigProjectComponent";
  }

  public void projectOpened() {
    // called when project is opened
    IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
    final Window window = (Window)frame;
    window.addWindowFocusListener(codeStyleManager);
  }

  public void projectClosed() {
    // called when project is being closed
    IdeFrame frame = WindowManager.getInstance().getIdeFrame(project);
    final Window window = (Window)frame;
    window.removeWindowFocusListener(codeStyleManager);
  }
}
