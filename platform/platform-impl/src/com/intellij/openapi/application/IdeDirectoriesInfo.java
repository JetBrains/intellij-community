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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Holds information about config, system, plugins, log directories created and used by the IDE.
 * See {@link PathManager} to understand how the paths of these directories are computed.
 */
public class IdeDirectoriesInfo implements Comparable<IdeDirectoriesInfo> {
  private final String mySelector;
  private final DirectoryInfo myTopLevelDirectory;
  private final List<DirectoryInfo> mySubDirectories;

  public IdeDirectoriesInfo(@NotNull String selector, @NotNull String topLevelDescriptor, @Nullable Path topLevelDirectory) {
    mySelector = selector;
    myTopLevelDirectory = new DirectoryInfo(topLevelDescriptor, topLevelDirectory);
    mySubDirectories = new ArrayList<>();
  }

  public String getSelector() {
    return mySelector;
  }

  public DirectoryInfo getTopLevelDirectory() {
    return myTopLevelDirectory;
  }

  public List<DirectoryInfo> getSubDirectories() {
    return mySubDirectories;
  }

  public void addSubDirectory(@NotNull String descriptor, @NotNull Path directory) {
    mySubDirectories.add(new DirectoryInfo(descriptor, directory));
  }

  @Override
  public int compareTo(@NotNull IdeDirectoriesInfo o) {
    return mySelector.compareTo(o.mySelector);
  }

  public Path findHomeLocatorFile() {
    for (DirectoryInfo dirInfo : this.getSubDirectories()) {
      Path dir = dirInfo.getDirectory();
      Path file = dir.resolve(".home");
      if (Files.isRegularFile(file)) {
        return file;
      }
    }
    return null;
  }

  public Path findIdeaLogFile() {
    for (DirectoryInfo dirInfo : this.getSubDirectories()) {
      Path dir = dirInfo.getDirectory();
      Path file = dir.resolve("idea.log");
      if (Files.isRegularFile(file)) {
        return file;
      }
      file = dir.resolve("log").resolve("idea.log");
      if (Files.isRegularFile(file)) {
        return file;
      }
    }
    return null;
  }

  public Instant calculateLastUsedTime() {
    Stream<Path> directories = Stream.concat(Stream.of(getTopLevelDirectory().getDirectory()),
                                             getSubDirectories().stream().map(dirInfo -> dirInfo.getDirectory()));
    Stream<Path> specificFiles = Stream.of(findHomeLocatorFile(), findIdeaLogFile());
    List<Path> paths = Stream.concat(directories, specificFiles)
                             .filter(path -> path != null)
                             .collect(Collectors.toList());

    Instant lastUsedTime = Instant.MIN;

    for (Path path : paths) {
      Instant lastModifiedTime = Instant.MIN;
      try {
        lastModifiedTime = Files.getLastModifiedTime(path).toInstant();
      }
      catch (IOException | SecurityException ignored) {
      }
      if (lastModifiedTime.compareTo(lastUsedTime) > 0) {
        lastUsedTime = lastModifiedTime;
      }
    }

    return lastUsedTime;
  }

  /**
   * Holds the path of a directory along with a short string descriptor.
   */
  public static class DirectoryInfo {
    private String myDescriptor;
    private Path myDirectory;

    public DirectoryInfo(@NotNull String descriptor, @Nullable Path directory) {
      myDescriptor = descriptor;
      myDirectory = directory;
    }

    public String getDescriptor() {
      return myDescriptor;
    }

    public Path getDirectory() {
      return myDirectory;
    }
  }
}
