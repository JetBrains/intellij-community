// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.oracle.javafx.scenebuilder.kit.editor.EditorController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.content.ContentPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.hierarchy.treeview.HierarchyTreeViewController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.inspector.InspectorPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.library.LibraryPanelController;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.control.SplitPane;

import javax.swing.*;

public class JavaFXPlatformHelper {
  public static void disableImplicitExit() {
    Platform.setImplicitExit(false);
  }

  public static void javafxInvokeLater(Runnable runnable) {
    Platform.runLater(runnable);
  }

  public static void setDefaultClassLoader(ClassLoader loader) {
    FXMLLoader.setDefaultClassLoader(loader);
  }

  public static JComponent createJFXPanel() {
    return new JFXPanel();
  }

  public static void setupJFXPanel(JComponent panel, EditorController editorController) {
    HierarchyTreeViewController componentTree = new HierarchyTreeViewController(editorController);
    ContentPanelController canvas = new ContentPanelController(editorController);
    InspectorPanelController propertyTable = new InspectorPanelController(editorController);
    LibraryPanelController palette = new LibraryPanelController(editorController);

    SplitPane leftPane = new SplitPane();
    leftPane.setOrientation(Orientation.VERTICAL);
    leftPane.getItems().addAll(palette.getPanelRoot(), componentTree.getPanelRoot());
    leftPane.setDividerPositions(0.5, 0.5);

    SplitPane.setResizableWithParent(leftPane, Boolean.FALSE);
    SplitPane.setResizableWithParent(propertyTable.getPanelRoot(), Boolean.FALSE);

    SplitPane mainPane = new SplitPane();

    mainPane.getItems().addAll(leftPane, canvas.getPanelRoot(), propertyTable.getPanelRoot());
    mainPane.setDividerPositions(0.11036789297658862, 0.8963210702341137);

    ((JFXPanel)panel).setScene(new Scene(mainPane, panel.getWidth(), panel.getHeight(), true, SceneAntialiasing.BALANCED));
  }

  public static Object createChangeListener(Runnable command) {
    return (ChangeListener<Number>)(observable, oldValue, newValue) -> command.run();
  }

  @SuppressWarnings("unchecked")
  public static void addListeners(EditorController controller, Object listener, Object selectionListener) {
    controller.getJobManager().revisionProperty().addListener((ChangeListener<Number>)listener);
    controller.getSelection().revisionProperty().addListener((ChangeListener<Number>)selectionListener);
  }

  @SuppressWarnings("unchecked")
  public static void removeListeners(EditorController editorController, Object listener, Object selectionListener) {
    if (editorController != null) {
      if (listener != null) {
        editorController.getJobManager().revisionProperty().removeListener((ChangeListener<Number>)listener);
      }
      if (selectionListener != null) {
        editorController.getSelection().revisionProperty().removeListener((ChangeListener<Number>)selectionListener);
      }
    }
  }
}
