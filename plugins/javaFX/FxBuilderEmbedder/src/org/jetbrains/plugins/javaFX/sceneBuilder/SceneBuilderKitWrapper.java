package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.oracle.javafx.scenebuilder.kit.editor.drag.source.AbstractDragSource;
import javafx.fxml.FXMLLoader;
import javafx.scene.input.DataFormat;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;

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