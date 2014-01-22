package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.oracle.javafx.scenebuilder.app.DocumentWindowController;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.fxml.FXMLLoader;

import javax.swing.*;
import java.io.File;

/**
 * @author Alexander Lobas
 */
public class SceneBuilderWrapper {
  //private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.javaFX.sceneBuilder.SceneBuilderWrapper");

  public static JComponent create(final String path) throws Exception {
    final EmbedSceneBuilderApp app = new EmbedSceneBuilderApp();

    FXMLLoader.setDefaultClassLoader(SceneBuilderWrapper.class.getClassLoader());

    final JFXPanel panel = new JFXPanel();
    Platform.runLater(new Runnable() {
      @Override
      public void run() {
        app.initWithFX();
        DocumentWindowController controller = new DocumentWindowController();
        app.add(controller);

        try {
          controller.loadFromFile(new File(path));
        }
        catch (Throwable e) {
          e.printStackTrace();
          //LOG.error(e);
        }

        panel.setScene(controller.getScene());
      }
    });

    return panel;
  }
}