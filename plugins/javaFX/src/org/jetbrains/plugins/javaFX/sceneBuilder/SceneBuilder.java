package org.jetbrains.plugins.javaFX.sceneBuilder;

import javax.swing.*;

/**
 * @author Alexander Lobas
 */
public interface SceneBuilder {
  JComponent getPanel();

  void reloadFile();

  void close();
}