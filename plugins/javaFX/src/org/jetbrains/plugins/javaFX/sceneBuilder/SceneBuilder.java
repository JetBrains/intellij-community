package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.openapi.project.Project;

import javax.swing.*;
import java.net.URL;

/**
 * @author Alexander Lobas
 */
public interface SceneBuilder {
  JComponent getPanel();

  boolean reload();

  void close();

  static SceneBuilder create(URL url, Project project, EditorCallback editorCallback) throws Exception {
    return new SceneBuilderImpl(url, project, editorCallback);
  }
}