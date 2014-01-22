package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.oracle.javafx.scenebuilder.app.AppPlatform;
import com.oracle.javafx.scenebuilder.app.DocumentWindowController;
import com.oracle.javafx.scenebuilder.app.SceneBuilderApp;
import com.oracle.javafx.scenebuilder.kit.editor.drag.source.AbstractDragSource;
import com.oracle.javafx.scenebuilder.kit.library.user.UserLibrary;
import javafx.scene.input.DataFormat;

import java.lang.reflect.Field;
import java.net.URL;
import java.util.*;

/**
 * @author Alexander Lobas
 */
public class EmbedSceneBuilderApp extends SceneBuilderApp {
  private List<DocumentWindowController> myControllers = new ArrayList<DocumentWindowController>();
  private UserLibrary myUserLibrary;

  public EmbedSceneBuilderApp() throws Exception {
    Field singleton = SceneBuilderApp.class.getDeclaredField("singleton");
    singleton.setAccessible(true);
    singleton.set(null, this);

    Field identifier = DataFormat.class.getDeclaredField("identifier");
    identifier.setAccessible(true);
    identifier.set(AbstractDragSource.INTERNAL_DATA_FORMAT, Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("application/scene.builder.internal"))));
  }

  public void initWithFX() {
    myUserLibrary = new UserLibrary(AppPlatform.getUserLibraryFolder());
    myUserLibrary.startWatching();
  }

  @Override
  public void performControlAction(ApplicationControlAction a, DocumentWindowController source) {
    System.out.println("performControlAction");
  }

  @Override
  public void documentWindowRequestClose(DocumentWindowController fromWindow) {
    System.out.println("documentWindowRequestClose");
  }

  @Override
  public UserLibrary getUserLibrary() {
    return myUserLibrary;
  }

  public void add(DocumentWindowController controller) {
    myControllers.add(controller);
  }

  @Override
  public DocumentWindowController lookupDocumentWindowControllers(URL fxmlLocation) {
    for (DocumentWindowController controller : myControllers) {
      URL url = controller.getEditorController().getFxmlLocation();
      if (url != null && url.equals(fxmlLocation)) {
        return controller;
      }
    }

    return null;
  }

  @Override
  public void toggleDebugMenu() {
    System.out.println("toggleDebugMenu");
  }
}