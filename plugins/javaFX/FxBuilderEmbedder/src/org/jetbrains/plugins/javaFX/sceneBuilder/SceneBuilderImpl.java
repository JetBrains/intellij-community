package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.oracle.javafx.scenebuilder.kit.editor.EditorController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.content.ContentPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.hierarchy.treeview.HierarchyTreeViewController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.inspector.InspectorPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.library.LibraryPanelController;
import com.oracle.javafx.scenebuilder.kit.fxom.FXOMDocument;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.control.SplitPane;

import javax.swing.*;
import java.net.URL;

/**
 * @author Alexander Lobas
 */
public class SceneBuilderImpl implements SceneBuilder {
  private final URL myFileURL;
  private final ErrorHandler myErrorHandler;
  private final JFXPanel myPanel = new JFXPanel();
  private EditorController myEditorController;

  public SceneBuilderImpl(URL url, ErrorHandler errorHandler) {
    myFileURL = url;
    myErrorHandler = errorHandler;

    Platform.runLater(new Runnable() {
      @Override
      public void run() {
        create();
      }
    });
  }

  private void create() {
    myEditorController = new EditorController();
    HierarchyTreeViewController componentTree = new HierarchyTreeViewController(myEditorController);
    ContentPanelController canvas = new ContentPanelController(myEditorController);
    InspectorPanelController propertyTable = new InspectorPanelController(myEditorController);
    LibraryPanelController palette = new LibraryPanelController(myEditorController);

    loadFile();

    SplitPane leftPane = new SplitPane();
    leftPane.setOrientation(Orientation.VERTICAL);
    leftPane.getItems().addAll(palette.getPanelRoot(), componentTree.getPanelRoot());
    leftPane.setDividerPositions(0.5, 0.5);

    SplitPane.setResizableWithParent(leftPane, Boolean.FALSE);
    SplitPane.setResizableWithParent(propertyTable.getPanelRoot(), Boolean.FALSE);

    SplitPane mainPane = new SplitPane();

    mainPane.getItems().addAll(leftPane, canvas.getPanelRoot(), propertyTable.getPanelRoot());
    mainPane.setDividerPositions(0.11036789297658862, 0.8963210702341137);

    myPanel.setScene(new Scene(mainPane, -1, -1, true, SceneAntialiasing.BALANCED));
  }

  @Override
  public JComponent getPanel() {
    return myPanel;
  }

  @Override
  public void reloadFile() {
    if (myEditorController != null) {
      Platform.runLater(new Runnable() {
        @Override
        public void run() {
          create();
        }
      });
    }
  }

  private void loadFile() {
    try {
      String fxmlText = FXOMDocument.readContentFromURL(myFileURL);
      myEditorController.setFxmlTextAndLocation(fxmlText, myFileURL);
    }
    catch (Throwable e) {
      myErrorHandler.handle(e);
    }
  }
}