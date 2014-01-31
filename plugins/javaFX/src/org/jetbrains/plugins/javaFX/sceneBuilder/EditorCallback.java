package org.jetbrains.plugins.javaFX.sceneBuilder;

/**
 * @author Alexander Lobas
 */
public interface EditorCallback {
  void saveChanges(String content);

  void handleError(Throwable e);
}