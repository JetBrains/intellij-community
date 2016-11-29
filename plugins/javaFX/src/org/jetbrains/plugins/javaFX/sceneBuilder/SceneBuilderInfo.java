package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.JavaFxSettings;
import org.jetbrains.plugins.javaFX.JavaFxSettingsConfigurable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class SceneBuilderInfo {
  public static final SceneBuilderInfo EMPTY = new SceneBuilderInfo(null, null);

  public final String path;
  public final String libPath;

  private SceneBuilderInfo(String path, String libPath) {
    this.path = path;
    this.libPath = libPath;
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof SceneBuilderInfo) {
      SceneBuilderInfo info = (SceneBuilderInfo)object;
      return Comparing.equal(path, info.path) && Comparing.equal(libPath, info.libPath);
    }
    return false;
  }

  @NotNull
  public static SceneBuilderInfo get(Project project, boolean choosePathIfEmpty) {
    JavaFxSettings settings = JavaFxSettings.getInstance();
    String pathToSceneBuilder = settings.getPathToSceneBuilder();

    if (StringUtil.isEmptyOrSpaces(pathToSceneBuilder) || !new File(pathToSceneBuilder).exists()) {
      VirtualFile sceneBuilderFile = null;
      if (choosePathIfEmpty) {
        sceneBuilderFile = FileChooser.chooseFile(JavaFxSettingsConfigurable.createSceneBuilderDescriptor(), project, getPredefinedPath());
      }
      if (sceneBuilderFile == null) {
        return EMPTY;
      }

      pathToSceneBuilder = FileUtil.toSystemIndependentName(sceneBuilderFile.getPath());
      settings.setPathToSceneBuilder(pathToSceneBuilder);
    }

    File sceneBuilderLibsFile;

    if (SystemInfo.isMac) {
      sceneBuilderLibsFile = new File(new File(pathToSceneBuilder, "Contents"), "Java");
    }
    else if (SystemInfo.isWindows) {
      File sceneBuilderRoot = new File(pathToSceneBuilder);
      File sceneBuilderRootDir = sceneBuilderRoot.getParentFile();
      if (sceneBuilderRootDir == null) {
        final File foundInPath = PathEnvironmentVariableUtil.findInPath(pathToSceneBuilder);
        if (foundInPath != null) {
          sceneBuilderRootDir = foundInPath.getParentFile();
        }
      }
      sceneBuilderRoot = sceneBuilderRootDir != null ? sceneBuilderRootDir.getParentFile() : null;
      if (sceneBuilderRoot != null) {
        final File appFile = new File(sceneBuilderRootDir, "app");
        if (appFile.isDirectory()) {
          sceneBuilderLibsFile = appFile;
        }
        else {
          final File libFile = new File(sceneBuilderRoot, "lib");
          sceneBuilderLibsFile = libFile.isDirectory() ? libFile : null;
        }
      }
      else {
        sceneBuilderLibsFile = null;
      }
    }
    else {
      sceneBuilderLibsFile = new File(new File(pathToSceneBuilder).getParent(), "app");
    }

    if (sceneBuilderLibsFile != null && (!sceneBuilderLibsFile.exists() || !sceneBuilderLibsFile.isDirectory())) {
      sceneBuilderLibsFile = null;
    }

    return new SceneBuilderInfo(pathToSceneBuilder, sceneBuilderLibsFile == null ? null : sceneBuilderLibsFile.getAbsolutePath());
  }

  @Nullable
  private static VirtualFile getPredefinedPath() {
    String path = null;
    if (SystemInfo.isWindows) {
      List<String> suspiciousPaths = new ArrayList<>();
      String programFiles = "C:\\Program Files";

      String sb20 = "\\JavaFX Scene Builder 2.0\\JavaFX Scene Builder 2.0.exe";
      String sb11 = "\\JavaFX Scene Builder 1.1\\JavaFX Scene Builder 1.1.exe";
      String sb10 = "\\JavaFX Scene Builder 1.0\\bin\\scenebuilder.exe";

      fillPaths(programFiles, suspiciousPaths, sb20, sb11, sb10);
      fillPaths(programFiles + " (x86)", suspiciousPaths, sb20, sb11, sb10);

      path = findFirstThatExist(ArrayUtil.toStringArray(suspiciousPaths));
    }
    else if (SystemInfo.isMac) {
      path = findFirstThatExist("/Applications/JavaFX Scene Builder 2.0.app",
                                "/Applications/JavaFX Scene Builder 1.1.app",
                                "/Applications/JavaFX Scene Builder 1.0.app");
    }
    else if (SystemInfo.isUnix) {
      path = findFirstThatExist("/opt/JavaFXSceneBuilder2.0/JavaFXSceneBuilder2.0", "/opt/JavaFXSceneBuilder1.1/JavaFXSceneBuilder1.1");
    }

    return path != null ? LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path)) : null;
  }

  private static String findFirstThatExist(String... paths) {
    File sb = FileUtil.findFirstThatExist(paths);
    return sb == null ? null : sb.getPath();
  }

  private static void fillPaths(String programFilesPath, List<String> suspiciousPaths, String... sb) {
    for (String sbi : sb) {
      suspiciousPaths.add(new File(programFilesPath, "Oracle").getPath() + sbi);
    }
  }
}