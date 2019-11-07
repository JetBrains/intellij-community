// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.sceneBuilder;

import com.intellij.openapi.application.PluginPathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.PathUtil;
import com.intellij.util.lang.JavaVersion;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class SceneBuilderUtil {
  private static final Logger LOG = Logger.getInstance(SceneBuilderUtil.class);
  private static final URLClassLoader ourLoader = createClassLoader();

  private static boolean isJava8() {
    return JavaVersion.current().feature == 8;
  }

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

  private static URL[] getLibUrls() {
    try {
      final Path javaFxJar = Paths.get(PathUtil.getJarPathForClass(SceneBuilderUtil.class));
      boolean isDevMode = Files.isDirectory(javaFxJar);
      final Path sceneBuilder;
      if (isJava8()) {
        if (isDevMode) {
          sceneBuilder = Paths.get(PluginPathManager.getPluginHomePath("javaFX")).resolve("lib/SceneBuilderKit-8.2.0.jar");
        }
        else {
          sceneBuilder = getJarPath("rt/java8/SceneBuilderKit-8.2.0.jar", javaFxJar);
        }
      }
      else {
        if (isDevMode) {
          throw new IllegalStateException(
            "Development mode is not supported for Scene Builder in JDK 11, please use full distribution or JDK 8");
        }
        sceneBuilder = getJarPath("rt/java11/scenebuilderkit-11.0.2.jar", javaFxJar);
      }
      final Path sceneBuilderImpl = getJarPath(isDevMode ? "intellij.javaFX.sceneBuilder" : "rt/sceneBuilderBridge.jar", javaFxJar);
      return new URL[]{sceneBuilder.toUri().toURL(), sceneBuilderImpl.toUri().toURL()};
    }
    catch (IOException e) {
      LOG.warn(e);
    }
    return new URL[]{};
  }

  private static Path getJarPath(@NotNull String relativePath, @NotNull Path javafxRuntimePath) {
    return javafxRuntimePath.getParent().resolve(relativePath);
  }
}