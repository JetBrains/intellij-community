package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.util.containers.FixedHashMap;
import com.intellij.util.containers.IntArrayList;
import com.oracle.javafx.scenebuilder.kit.editor.EditorController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.content.ContentPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.hierarchy.treeview.HierarchyTreeViewController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.inspector.InspectorPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.panel.library.LibraryPanelController;
import com.oracle.javafx.scenebuilder.kit.editor.selection.AbstractSelectionGroup;
import com.oracle.javafx.scenebuilder.kit.editor.selection.ObjectSelectionGroup;
import com.oracle.javafx.scenebuilder.kit.fxom.*;
import com.oracle.javafx.scenebuilder.kit.metadata.util.PropertyName;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.embed.swing.JFXPanel;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.control.SplitPane;

import javax.swing.*;
import java.net.URL;
import java.util.*;

/**
 * @author Alexander Lobas
 */
public class SceneBuilderImpl implements SceneBuilder {
  private final URL myFileURL;
  private final EditorCallback myEditorCallback;
  private final JFXPanel myPanel = new JFXPanel();
  private EditorController myEditorController;
  private volatile boolean mySkipChanges;
  private ChangeListener<Number> myListener;
  private ChangeListener<Number> mySelectionListener;
  private final Map<String, int[][]> mySelectionState = new FixedHashMap<String, int[][]>(16);

  public SceneBuilderImpl(URL url, EditorCallback editorCallback) {
    myFileURL = url;
    myEditorCallback = editorCallback;

    Platform.setImplicitExit(false);

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

    loadFile();
    startChangeListener();

    UsageTrigger.trigger("scene-builder.open");
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
          loadFile();
        }
      });
    }
  }

  private void startChangeListener() {
    myListener = new ChangeListener<Number>() {
      @Override
      public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        if (!mySkipChanges) {
          myEditorCallback.saveChanges(myEditorController.getFxmlText());
          UsageTrigger.trigger("scene-builder.edit");
        }
      }
    };
    mySelectionListener = new ChangeListener<Number>() {
      @Override
      public void changed(ObservableValue<? extends Number> observable, Number oldValue, Number newValue) {
        if (!mySkipChanges) {
          int[][] state = getSelectionState();
          if (state != null) {
            mySelectionState.put(myEditorController.getFxmlText(), state);
          }
        }
      }
    };

    myEditorController.getJobManager().revisionProperty().addListener(myListener);
    myEditorController.getSelection().revisionProperty().addListener(mySelectionListener);
  }

  @Override
  public void close() {
    if (myEditorController != null) {
      if (mySelectionListener != null) {
        myEditorController.getSelection().revisionProperty().removeListener(mySelectionListener);
      }
      if (myListener != null) {
        myEditorController.getJobManager().revisionProperty().removeListener(myListener);
      }
    }
  }

  private void loadFile() {
    mySkipChanges = true;

    try {
      String fxmlText = FXOMDocument.readContentFromURL(myFileURL);
      myEditorController.setFxmlTextAndLocation(fxmlText, myFileURL);

      int[][] selectionState = mySelectionState.get(fxmlText);
      if (selectionState != null) {
        restoreSelection(selectionState);
      }
    }
    catch (Throwable e) {
      myEditorCallback.handleError(e);
    }
    finally {
      mySkipChanges = false;
    }
  }

  private int[][] getSelectionState() {
    AbstractSelectionGroup group = myEditorController.getSelection().getGroup();
    if (group instanceof ObjectSelectionGroup) {
      Set<FXOMObject> items = ((ObjectSelectionGroup)group).getItems();
      int[][] state = new int[items.size()][];
      int index = 0;

      for (FXOMObject item : items) {
        IntArrayList path = new IntArrayList();
        componentToPath(item, path);
        state[index++] = path.toArray();
      }

      return state;
    }

    return null;
  }

  private static void componentToPath(FXOMObject component, IntArrayList path) {
    FXOMObject parent = component.getParentObject();

    if (parent != null) {
      path.add(0, component.getParentProperty().getValues().indexOf(component));
      componentToPath(parent, path);
    }
  }

  private void restoreSelection(int[][] state) {
    Collection<FXOMObject> newSelection = new ArrayList<FXOMObject>();
    FXOMObject rootComponent = myEditorController.getFxomDocument().getFxomRoot();

    for (int[] path : state) {
      pathToComponent(newSelection, rootComponent, path, 0);
    }

    myEditorController.getSelection().select(newSelection);
  }

  private static void pathToComponent(Collection<FXOMObject> components, FXOMObject component, int[] path, int index) {
    if (index == path.length) {
      components.add(component);
    }
    else {
      List<FXOMObject> children = Collections.emptyList();
      Map<PropertyName, FXOMProperty> properties = ((FXOMInstance)component).getProperties();
      for (Map.Entry<PropertyName, FXOMProperty> entry : properties.entrySet()) {
        FXOMProperty value = entry.getValue();
        if (value instanceof FXOMPropertyC) {
          children = ((FXOMPropertyC)value).getValues();
          break;
        }
      }

      int componentIndex = path[index];
      if (0 <= componentIndex && componentIndex < children.size()) {
        pathToComponent(components, children.get(componentIndex), path, index + 1);
      }
    }
  }
}