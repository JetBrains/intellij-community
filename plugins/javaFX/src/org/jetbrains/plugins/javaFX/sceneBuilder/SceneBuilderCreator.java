package org.jetbrains.plugins.javaFX.sceneBuilder;

import java.net.URL;

/**
 * @author Alexander Lobas
 */
public interface SceneBuilderCreator {
  State getState();

  SceneBuilder create(URL url, EditorCallback editorCallback) throws Exception;
}