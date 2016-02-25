package org.jetbrains.plugins.javaFX.sceneBuilder;

import javafx.fxml.FXMLLoader;

import java.net.URL;

/**
 * @author Alexander Lobas
 */
public class SceneBuilderKitWrapper {
  public static SceneBuilder create(URL url, EditorCallback editorCallback) throws Exception {
    // JavaFX class loading fix
    FXMLLoader.setDefaultClassLoader(SceneBuilderKitWrapper.class.getClassLoader());

    return new SceneBuilderImpl(url, editorCallback);
  }
}