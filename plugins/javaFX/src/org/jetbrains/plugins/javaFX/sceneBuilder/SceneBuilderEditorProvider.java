package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorPolicy;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxFileTypeFactory;

/**
 * @author Alexander Lobas
 */
public class SceneBuilderEditorProvider implements FileEditorProvider, DumbAware{
  static {
    ClassLoader pluginClassLoader = SceneBuilderEditorProvider.class.getClassLoader();
    pluginClassLoader.setPackageAssertionStatus("com.oracle.javafx.scenebuilder.kit", false);
  }

  @Override
  public boolean accept(@NotNull Project project, @NotNull VirtualFile file) {
    return JavaFxFileTypeFactory.FXML_EXTENSION.equalsIgnoreCase(file.getExtension()) &&
           SystemInfo.isJavaVersionAtLeast("1.8") &&
           Registry.is("embed.scene.builder");
  }

  @NotNull
  @Override
  public FileEditor createEditor(@NotNull Project project, @NotNull VirtualFile file) {
    return new SceneBuilderEditor(project, file);
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
}