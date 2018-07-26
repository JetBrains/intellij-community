/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.application;

import com.intellij.openapi.application.impl.ApplicationInfoImpl;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.SystemProperties;
import org.jetbrains.annotations.NotNull;

import java.io.IOError;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DeleteOldDirectoriesSettings {
  private final Path userHome;
  private final String selector;
  private final boolean isMac;
  private final boolean isEAP;
  private final List<PathWithTransformations> ideDirectories;
  private final String applicationInfoFilePath;

  public DeleteOldDirectoriesSettings() {
    userHome = Paths.get(SystemProperties.getUserHome());
    String sel = PathManager.getPathsSelector();
    selector = sel != null ? sel : "";
    isMac = SystemInfo.isMac;
    isEAP = ApplicationInfoImpl.getShadowInstance().isEAP();
    ideDirectories = Stream.of(PathManager.getConfigPath(),
                               PathManager.getSystemPath(),
                               PathManager.getPluginsPath(),
                               PathManager.getLogPath())
                           .map(PathWithTransformations::new)
                           .collect(Collectors.toList());
    applicationInfoFilePath = "idea/" + ApplicationNamesInfo.getComponentName() + ".xml";
  }

  /**
   * Home directory of the user.
   */
  @NotNull
  public Path getUserHome() { return userHome; }

  /**
   * The paths selector that is used to determine the name of the IDE directories.
   * E.g. IdeaIC2017.3, AndroidStudio3.0.  Empty string means no paths selector set.
   */
  @NotNull
  public String getSelector() { return selector; }

  /**
   * Whether the operating system is Mac OS.
   */
  public boolean isMac() { return isMac; }

  /**
   * Whether the IDE build is from Early Access Program (EAP).
   */
  public boolean isEAP() { return isEAP; }

  /**
   * The paths to IDE directories i.e. config, system, plugins, log directories.
   * The paths and their possible transformations are returned.
   */
  @NotNull
  public List<PathWithTransformations> getIdeDirectories() { return ideDirectories; }

  /**
   * The path to the ApplicationInfo XML file within resources.jar shipped with the IDE.
   */
  @NotNull
  public String getApplicationInfoFilePath() { return applicationInfoFilePath; }

  /**
   * Stores a path along with its transformations to normalized, absolute, real, canonical
   * paths achieved using the methods of {@link java.io.File} and {@link java.nio.file.Path}.
   */
  public static class PathWithTransformations implements Iterable<String> {
    private final Set<String> pathWithTransformations;

    public PathWithTransformations(@NotNull String path) {
      pathWithTransformations = new HashSet<>();

      pathWithTransformations.add(path);

      Path p = Paths.get(path);
      pathWithTransformations.add(p.toString());
      pathWithTransformations.add(p.normalize().toString());
      try {
        pathWithTransformations.add(p.toAbsolutePath().toString());
        pathWithTransformations.add(p.toAbsolutePath().normalize().toString());
      }
      catch (IOError | SecurityException ignored) {
      }
      try {
        pathWithTransformations.add(p.toRealPath().toString());
      }
      catch (IOException | SecurityException ignored) {
      }
      try {
        pathWithTransformations.add(p.toFile().getCanonicalPath());
      }
      catch (IOException | SecurityException ignored) {
      }
    }

    @NotNull
    @Override
    public Iterator<String> iterator() {
      return pathWithTransformations.iterator();
    }
  }
}
