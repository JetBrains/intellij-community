// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.execution.configurations.PathEnvironmentVariableUtil;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.system.OS;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.javaFX.JavaFxSettings;
import org.jetbrains.plugins.javaFX.JavaFxSettingsConfigurable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SceneBuilderInfo {
  public static final SceneBuilderInfo EMPTY = new SceneBuilderInfo(null, null);

  public final String path;
  public final String libPath;

  private SceneBuilderInfo(String path, String libPath) {
    this.path = path;
    this.libPath = libPath;
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof SceneBuilderInfo info) {
      return Objects.equals(path, info.path) && Objects.equals(libPath, info.libPath);
    }
    return false;
  }

  public static @NotNull SceneBuilderInfo get(Project project, boolean choosePathIfEmpty) {
    var settings = JavaFxSettings.getInstance();
    var pathToSceneBuilder = settings.getPathToSceneBuilder();

    if (pathToSceneBuilder == null || pathToSceneBuilder.isBlank() || !Files.exists(Path.of(pathToSceneBuilder))) {
      VirtualFile sceneBuilderFile = null;
      if (choosePathIfEmpty) {
        sceneBuilderFile = FileChooser.chooseFile(JavaFxSettingsConfigurable.createSceneBuilderDescriptor(), project, getPredefinedPath());
      }
      if (sceneBuilderFile == null) {
        return EMPTY;
      }

      pathToSceneBuilder = sceneBuilderFile.getPath();
      settings.setPathToSceneBuilder(pathToSceneBuilder);
    }

    Path sceneBuilderLibsFile;

    if (OS.CURRENT == OS.macOS) {
      sceneBuilderLibsFile = Path.of(pathToSceneBuilder, "Contents", "Java");
    }
    else if (OS.CURRENT == OS.Windows) {
      var sceneBuilderRoot = Path.of(pathToSceneBuilder);
      var sceneBuilderRootDir = sceneBuilderRoot.getParent();
      if (sceneBuilderRootDir == null) {
        var foundInPath = PathEnvironmentVariableUtil.findFirst(pathToSceneBuilder);
        if (foundInPath != null && foundInPath.getParent() != null) {
          sceneBuilderRootDir = foundInPath.getParent();
        }
      }
      sceneBuilderRoot = sceneBuilderRootDir != null ? sceneBuilderRootDir.getParent() : null;
      if (sceneBuilderRoot != null) {
        var appFile = sceneBuilderRootDir.resolve("app");
        if (Files.isDirectory(appFile)) {
          sceneBuilderLibsFile = appFile;
        }
        else {
          var libFile = sceneBuilderRoot.resolve("lib");
          sceneBuilderLibsFile = Files.isDirectory(libFile) ? libFile : null;
        }
      }
      else {
        sceneBuilderLibsFile = null;
      }
    }
    else {
      sceneBuilderLibsFile = Path.of(pathToSceneBuilder).resolveSibling("app");
    }

    if (sceneBuilderLibsFile != null && !Files.isDirectory(sceneBuilderLibsFile)) {
      sceneBuilderLibsFile = null;
    }

    return new SceneBuilderInfo(pathToSceneBuilder, sceneBuilderLibsFile == null ? null : sceneBuilderLibsFile.toString());
  }

  private static @Nullable VirtualFile getPredefinedPath() {
    var path = switch (OS.CURRENT) {
      case Windows -> {
        var suspiciousPaths = new ArrayList<String>();
        var programFiles = "C:\\Program Files";

        var sb20 = "\\JavaFX Scene Builder 2.0\\JavaFX Scene Builder 2.0.exe";
        var sb11 = "\\JavaFX Scene Builder 1.1\\JavaFX Scene Builder 1.1.exe";
        var sb10 = "\\JavaFX Scene Builder 1.0\\bin\\scenebuilder.exe";

        fillPaths(programFiles, suspiciousPaths, sb20, sb11, sb10);
        fillPaths(programFiles + " (x86)", suspiciousPaths, sb20, sb11, sb10);

        yield findFirstThatExist(ArrayUtilRt.toStringArray(suspiciousPaths));
      }
      case macOS -> findFirstThatExist(
        "/Applications/JavaFX Scene Builder 2.0.app",
        "/Applications/JavaFX Scene Builder 1.1.app",
        "/Applications/JavaFX Scene Builder 1.0.app"
      );
      default -> findFirstThatExist(
        "/opt/JavaFXSceneBuilder2.0/JavaFXSceneBuilder2.0",
        "/opt/JavaFXSceneBuilder1.1/JavaFXSceneBuilder1.1"
      );
    };

    return path != null ? LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path)) : null;
  }

  private static String findFirstThatExist(String... paths) {
    var sb = FileUtil.findFirstThatExist(paths);
    return sb == null ? null : sb.getPath();
  }

  private static void fillPaths(String programFilesPath, List<String> suspiciousPaths, String... sb) {
    for (var sbi : sb) {
      suspiciousPaths.add(Path.of(programFilesPath, "Oracle") + sbi);
    }
  }
}
