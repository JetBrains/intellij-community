// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.jarRepository.JarRepositoryManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.javaFX.fxml.JavaFxCommonNames;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class SceneBuilderUtil {
  private static final Logger LOG = Logger.getInstance(SceneBuilderUtil.class);

  static final String SCENE_BUILDER_VERSION = "11.0.5";
  static final String JAVAFX_VERSION = "11.0.1";
  static final String SCENE_BUILDER_KIT_FULL_NAME = "scenebuilderkit-" + SCENE_BUILDER_VERSION + ".jar";

  public static final String[] JAVAFX_ARTIFACTS = {
    "javafx-fxml",
    "javafx-controls",
    "javafx-graphics",
    "javafx-base",
    "javafx-swing",
    "javafx-media",
    "javafx-web",
  };
  private static URLClassLoader ourLoader = createClassLoader();

  static SceneBuilder create(URL url, Project project, EditorCallback editorCallback) throws Exception {
    //noinspection unchecked
    Class<SceneBuilder> java11Class =
      (Class<SceneBuilder>)ourLoader.loadClass("org.jetbrains.plugins.javaFX.sceneBuilder.SceneBuilderImpl");
    Constructor<SceneBuilder> constructor = java11Class.getConstructor(URL.class, Project.class, EditorCallback.class, ClassLoader.class);
    return constructor.newInstance(url, project, editorCallback, ourLoader);
  }

  private static URLClassLoader createClassLoader() {
    return new URLClassLoader(getLibUrls(), SceneBuilderUtil.class.getClassLoader());
  }

  public static void updateLoader() {
    ourLoader = createClassLoader();
  }

  private static URL[] getLibUrls() {
    try {
      final Path javaFxJar = Paths.get(PathUtil.getJarPathForClass(SceneBuilderUtil.class));
      boolean isDevMode = Files.isDirectory(javaFxJar);
      final Path sceneBuilder = getSceneBuilder11Path();
      final Path sceneBuilderImpl = getJarPath(isDevMode ? "intellij.javaFX.sceneBuilder" : "rt/sceneBuilderBridge.jar", javaFxJar);

      try {
        Class.forName(JavaFxCommonNames.JAVAFX_SCENE_NODE);
      }
      catch (ClassNotFoundException e) {
        try {
          List<URL> urls = new ArrayList<>();
          urls.add(sceneBuilder.toUri().toURL());
          urls.add(sceneBuilderImpl.toUri().toURL());
          addJavafxFromLocalRepository(urls);

          return urls.toArray(new URL[0]);
        }
        catch (IOException ignored) {
          //keep only scene builder in classloader
        }
      }

      return new URL[]{sceneBuilder.toUri().toURL(), sceneBuilderImpl.toUri().toURL()};
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    return new URL[]{};
  }

  private static void addJavafxFromLocalRepository(List<URL> urls) throws IOException {
    File localRepositoryPath = JarRepositoryManager.getLocalRepositoryPath();
    Path javaFx = Paths.get(localRepositoryPath.getPath(), "org", "openjfx");

    for (String artifact : JAVAFX_ARTIFACTS) {
      Path path2Artifact = javaFx.resolve(artifact).resolve(JAVAFX_VERSION);

      List<Path> paths = Files
        .list(path2Artifact)
        .filter(path -> {
          String name = path.toFile().getName();
          return name.startsWith(artifact + "-" + JAVAFX_VERSION) && name.endsWith(".jar"); //include os-specific jars
        }).collect(Collectors.toList());

      for (Path path : paths) {
        urls.add(path.toUri().toURL());
      }
    }
  }

  static Path getSceneBuilder11Path() {
    return Paths.get(PathManager.getConfigPath(), "plugins", "javaFX", "rt", SCENE_BUILDER_VERSION).resolve(SCENE_BUILDER_KIT_FULL_NAME);
  }

  private static Path getJarPath(@NotNull String relativePath, @NotNull Path javafxRuntimePath) {
    return javafxRuntimePath.getParent().resolve(relativePath);
  }
}