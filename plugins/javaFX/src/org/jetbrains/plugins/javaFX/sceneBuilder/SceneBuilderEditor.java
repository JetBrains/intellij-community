package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.codeHighlighting.BackgroundEditorHighlighter;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorLocation;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.fileEditor.FileEditorStateLevel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

/**
 * @author Alexander Lobas
 */
public class SceneBuilderEditor extends UserDataHolderBase implements FileEditor {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.javaFX.sceneBuilder.SceneBuilderEditor");

  private final static String SCENE_CARD = "scene_builder";
  private final static String ERROR_CARD = "error";

  private final Project myProject;
  private final VirtualFile myFile;

  private final CardLayout myLayout = new CardLayout();
  private final JPanel myPanel = new JPanel(myLayout);

  private final JPanel myErrorPanel = new JPanel(new VerticalFlowLayout(VerticalFlowLayout.TOP, 10, 5, true, false));
  private final HyperlinkLabel myErrorLabel = new HyperlinkLabel();

  private JComponent myFxPanel;
  private ClassLoader mySceneLoader;

  public SceneBuilderEditor(@NotNull Project project, @NotNull VirtualFile file) {
    myProject = project;
    myFile = file;

    createErrorPage();

    initSceneBuilder(false);
  }

  private void createErrorPage() {
    myErrorLabel.setOpaque(false);

    myErrorLabel.addHyperlinkListener(new HyperlinkListener() {
      @Override
      public void hyperlinkUpdate(HyperlinkEvent e) {
        initSceneBuilder(true);
      }
    });

    myErrorPanel.add(myErrorLabel);
    myPanel.add(myErrorPanel);
  }

  private void showErrorPage(SceneBuilderInfo info, Throwable e) {
    if (e == null) {
      if (info == SceneBuilderInfo.EMPTY) {
        myErrorLabel.setHyperlinkText("Please configure JavaFX Scene Builder ", "path", "");
      }
      else {
        myErrorLabel.setHyperlinkText("Please reconfigure JavaFX Scene Builder ", "path", "");
      }
      myErrorLabel.setIcon(Messages.getWarningIcon());
    }
    else {
      myErrorLabel.setHyperlinkText("Error: " + e.getMessage(), "", "");
      myErrorLabel.setIcon(Messages.getErrorIcon());

      LOG.error(e);
    }
    myLayout.show(myPanel, ERROR_CARD);
  }

  private void initSceneBuilder(boolean choosePathIfEmpty) {
    SceneBuilderInfo info = SceneBuilderInfo.get(myProject, choosePathIfEmpty);

    if (info == SceneBuilderInfo.EMPTY || info.libPath == null) {
      showErrorPage(info, null);
    }
    else {
      try {
        loadSceneBuilder(info);
      }
      catch (Throwable e) {
        showErrorPage(info, e);
      }
    }
  }

  private void loadSceneBuilder(SceneBuilderInfo info) throws Exception {
    mySceneLoader = createSceneLoader(info);

    Class<?> wrapperClass = Class.forName("org.jetbrains.plugins.javaFX.sceneBuilder.SceneBuilderKitWrapper", false, mySceneLoader);
    myFxPanel = (JComponent)wrapperClass.getMethod("create", String.class).invoke(null, myFile.getPath());

    myPanel.add(myFxPanel, SCENE_CARD);
    myLayout.show(myPanel, SCENE_CARD);
  }

  private static ClassLoader createSceneLoader(SceneBuilderInfo info) throws Exception {
    List<URL> urls = new ArrayList<URL>();

    File[] files = new File(info.libPath).listFiles();
    if (files == null) {
      throw new Exception(info.libPath + " wrong path");
    }

    for (File libFile : files) {
      if (libFile.isFile() && libFile.getName().endsWith(".jar")) {
        if (libFile.getName().equalsIgnoreCase("SceneBuilderApp.jar")) {
          JarFile appJar = new JarFile(libFile);
          String version = appJar.getManifest().getMainAttributes().getValue("Implementation-Version");
          appJar.close();

          if (version != null) {
            int index = version.indexOf(" ");
            if (index != -1) {
              version = version.substring(0, index);
            }
          }

          if (StringUtil.compareVersionNumbers(version, "2.0") < 0) {
            throw new Exception(info.path + " wrong version: " + version);
          }
        }

        urls.add(libFile.toURI().toURL());
      }
    }

    if (urls.isEmpty()) {
      throw new Exception(info.libPath + " no jar found");
    }

    final String parent = new File(PathUtil.getJarPathForClass(SceneBuilderEditor.class)).getParent();
    if (SceneBuilderEditor.class.getClassLoader() instanceof PluginClassLoader) {
      urls.add(new File(new File(parent).getParent(), "embedder.jar").toURI().toURL());
    } else {
      final File localEmbedder = new File(parent, "FXBuilderEmbedder");
      if (localEmbedder.exists()) {
        urls.add(localEmbedder.toURI().toURL());
      } else {
        File home = new File(PathManager.getHomePath(), "community");
        if (!home.exists()) {
          home = new File(PathManager.getHomePath());
        }
        urls.add(new File(home, "plugins/JavaFX/FxBuilderEmbedder/lib/embedder.jar").toURI().toURL());
      }
    }
    return new URLClassLoader(urls.toArray(new URL[urls.size()]));
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return myPanel;
  }

  @Nullable
  @Override
  public JComponent getPreferredFocusedComponent() {
    return myFxPanel == null ? myErrorPanel : myFxPanel;
  }

  @Override
  public void dispose() {
    if (myFxPanel != null) {
      mySceneLoader = null;
      // XXX
    }
  }

  @NotNull
  @Override
  public String getName() {
    return "Scene Builder";
  }

  @Override
  public void selectNotify() {
    // TODO: Auto-generated method stub
  }

  @Override
  public void deselectNotify() {
    // TODO: Auto-generated method stub
  }

  @NotNull
  @Override
  public FileEditorState getState(@NotNull FileEditorStateLevel level) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void setState(@NotNull FileEditorState state) {
  }

  @Override
  public boolean isValid() {
    return myFile.isValid();
  }

  @Override
  public boolean isModified() {
    return true;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Override
  public void removePropertyChangeListener(@NotNull PropertyChangeListener listener) {
  }

  @Nullable
  @Override
  public BackgroundEditorHighlighter getBackgroundHighlighter() {
    return null;
  }

  @Nullable
  @Override
  public FileEditorLocation getCurrentLocation() {
    return null;
  }

  @Nullable
  @Override
  public StructureViewBuilder getStructureViewBuilder() {
    return null;
  }
}