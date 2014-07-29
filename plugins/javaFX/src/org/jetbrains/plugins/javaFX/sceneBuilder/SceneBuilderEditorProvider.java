package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.fileEditor.FileEditorState;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

/**
 * @author Alexander Lobas
 */
public class SceneBuilderEditorProvider implements FileEditorProvider, DumbAware, SceneBuilderProvider {
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.javaFX.sceneBuilder.SceneBuilderEditorProvider");

  private SceneBuilderCreatorImpl mySceneBuilderCreator;

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return JavaFxFileTypeFactory.FXML_EXTENSION.equalsIgnoreCase(file.getExtension()) &&
           SystemInfo.isJavaVersionAtLeast("1.8") &&
           Registry.is("embed.scene.builder");
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new SceneBuilderEditor(project, file, this);
  }

  @Override
  public void disposeEditor(@NotNull FileEditor editor) {
    Disposer.dispose(editor);
  }

  @NotNull
  @Override
  public FileEditorState readState(@NotNull Element sourceElement, @NotNull Project project, @NotNull VirtualFile file) {
    return FileEditorState.INSTANCE;
  }

  @Override
  public void writeState(@NotNull FileEditorState state, @NotNull Project project, @NotNull Element targetElement) {
  }

  @NotNull
  @Override
  public String getEditorTypeId() {
    return "JavaFX-Scene-Builder";
  }

  @NotNull
  @Override
  public FileEditorPolicy getPolicy() {
    return FileEditorPolicy.PLACE_AFTER_DEFAULT_EDITOR;
  }

  @Override
  public SceneBuilderCreator get(Project project, boolean choosePathIfEmpty) {
    if (mySceneBuilderCreator == null) {
      SceneBuilderInfo info = SceneBuilderInfo.get(project, choosePathIfEmpty);
      if (info == SceneBuilderInfo.EMPTY) {
        return new ErrorSceneBuilderCreator(State.EMPTY_PATH);
      }
      if (info.libPath == null) {
        return new ErrorSceneBuilderCreator(State.ERROR_PATH);
      }

      // TODO: handle change configuration

      try {
        mySceneBuilderCreator = new SceneBuilderCreatorImpl(info);
      }
      catch (Throwable e) {
        LOG.error(e);
        return new ErrorSceneBuilderCreator(State.CREATE_ERROR);
      }
    }
    return mySceneBuilderCreator;
  }
}