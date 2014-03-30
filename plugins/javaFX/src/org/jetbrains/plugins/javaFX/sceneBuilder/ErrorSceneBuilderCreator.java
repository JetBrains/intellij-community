package org.jetbrains.plugins.javaFX.sceneBuilder;

import java.net.URL;

/**
 * @author Alexander Lobas
 */
public class ErrorSceneBuilderCreator implements SceneBuilderCreator {
  private final State myState;

  public ErrorSceneBuilderCreator(State state) {
    myState = state;
  }

  @Override
  public State getState() {
    return myState;
  }

  @Override
  public SceneBuilder create(URL url, EditorCallback editorCallback) throws Exception {
    throw new UnsupportedOperationException();
  }
}