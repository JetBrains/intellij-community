// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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