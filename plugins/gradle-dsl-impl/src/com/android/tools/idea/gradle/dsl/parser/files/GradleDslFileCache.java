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
package com.android.tools.idea.gradle.dsl.parser.files;

import static com.android.tools.idea.gradle.dsl.GradleUtil.getBaseDirPath;
import static com.android.tools.idea.gradle.dsl.GradleUtil.getGradleSettingsFile;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModelImpl;
import com.android.tools.idea.gradle.dsl.parser.BuildModelContext;
import com.google.common.base.Charsets;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Cache to store a mapping between file paths and their respective {@link GradleDslFileCache} objects, its main purpose it to
 * prevent the parsing of a file more than once. In large projects without caching the parsed file we can end up parsing the same
 * file hundreds of times.
 */
public class GradleDslFileCache {
  public static final Logger LOG = Logger.getInstance(GradleDslFileCache.class);
  @NotNull private Project myProject;
  @NotNull private Map<String, GradleDslFile> myParsedBuildFiles = new HashMap<>();
  @NotNull private Deque<VirtualFile> myParsingStack = new ArrayDeque<>();

  public GradleDslFileCache(@NotNull Project project) {
    myProject = project;
  }

  public void clearAllFiles() {
    myParsedBuildFiles.clear();
  }

  @NotNull
  public GradleBuildFile getOrCreateBuildFile(@NotNull VirtualFile file,
                                              @NotNull String name,
                                              @NotNull BuildModelContext context,
                                              boolean isApplied) {
    GradleDslFile dslFile = myParsedBuildFiles.get(file.getUrl());
    if (dslFile == null) {
      myParsingStack.push(file);
      dslFile = GradleBuildModelImpl.parseBuildFile(file, myProject, name, context, isApplied);
      myParsingStack.pop();
      myParsedBuildFiles.put(file.getUrl(), dslFile);
    }
    else if (!(dslFile instanceof GradleBuildFile)) {
      throw new IllegalStateException("Found wrong type for build file in cache!");
    }

    return (GradleBuildFile)dslFile;
  }

  public void putBuildFile(@NotNull String name, @NotNull GradleDslFile buildFile) {
    myParsedBuildFiles.put(name, buildFile);
  }

  /**
   * @return the first original file that was being parsed, this is used to resolve relative paths.
   */
  @Nullable
  public VirtualFile getCurrentParsingRoot() {
    return myParsingStack.isEmpty() ? null : myParsingStack.getLast();
  }

  @Nullable
  public GradleSettingsFile getSettingsFile(@NotNull Project project) {
    VirtualFile file = getGradleSettingsFile(getBaseDirPath(project));
    if (file == null) {
      return null;
    }

    GradleDslFile dslFile = myParsedBuildFiles.get(file.getUrl());
    if (dslFile != null && !(dslFile instanceof GradleSettingsFile)) {
      throw new IllegalStateException("Found wrong type for settings file in cache!");
    }
    return (GradleSettingsFile)dslFile;
  }

  @NotNull
  public GradleSettingsFile getOrCreateSettingsFile(@NotNull VirtualFile settingsFile, @NotNull BuildModelContext context) {
    GradleDslFile dslFile = myParsedBuildFiles.get(settingsFile.getUrl());
    if (dslFile == null) {
      dslFile = new GradleSettingsFile(settingsFile, myProject, "settings", context);
      dslFile.parse();
      myParsedBuildFiles.put(settingsFile.getUrl(), dslFile);
    }
    else if (!(dslFile instanceof GradleSettingsFile)) {
      throw new IllegalStateException("Found wrong type for settings file in cache!");
    }
    return (GradleSettingsFile)dslFile;
  }

  @Nullable
  public GradlePropertiesFile getOrCreatePropertiesFile(@NotNull VirtualFile file,
                                                        @NotNull String moduleName,
                                                        @NotNull BuildModelContext context) {
    GradleDslFile dslFile = myParsedBuildFiles.get(file.getUrl());
    if (dslFile == null) {
      try {
        Properties properties = getProperties(file);
        dslFile = new GradlePropertiesFile(properties, file, myProject, moduleName, context);
        myParsedBuildFiles.put(file.getUrl(), dslFile);
      }
      catch (IOException e) {
        LOG.warn("Failed to process properties file " + file.getPath(), e);
        return null;
      }
    }
    else if (!(dslFile instanceof GradlePropertiesFile)) {
      throw new IllegalStateException("Found wrong type for properties file in cache!");
    }
    return (GradlePropertiesFile)dslFile;
  }

  private static Properties getProperties(@NotNull VirtualFile file) throws IOException {
    Properties properties = new Properties();
    properties.load(new InputStreamReader(file.getInputStream(), Charsets.UTF_8));
    return properties;
  }

  @NotNull
  public List<GradleDslFile> getAllFiles() {
    return new ArrayList<>(myParsedBuildFiles.values());
  }
}
