/*
 * Copyright 2000-2007 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.coverage;

import org.jetbrains.annotations.ApiStatus;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * @author Eugene Zhuravlev
 */
public final class DefaultCoverageFileProvider implements CoverageFileProvider {
  @ApiStatus.Internal
  public static final String DEFAULT_LOCAL_PROVIDER_KEY = DefaultCoverageFileProvider.class.getName();

  private final Path myFile;
  private final String mySourceProvider;

  public DefaultCoverageFileProvider(String path) {
    this(Path.of(path), DEFAULT_LOCAL_PROVIDER_KEY);
  }

  public DefaultCoverageFileProvider(Path file) {
    this(file, DEFAULT_LOCAL_PROVIDER_KEY);
  }

  /**
   * @deprecated Use {@link #DefaultCoverageFileProvider(Path)} instead.
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  public DefaultCoverageFileProvider(File file) {
    this(file.toPath(), DEFAULT_LOCAL_PROVIDER_KEY);
  }

  /**
   * @deprecated Use {@link #DefaultCoverageFileProvider(Path, String)} instead.
   */
  @SuppressWarnings("IO_FILE_USAGE")
  @Deprecated
  public DefaultCoverageFileProvider(File file, String sourceProvider) {
    this(file.toPath(), sourceProvider);
  }

  public DefaultCoverageFileProvider(Path file, String sourceProvider) {
    myFile = file;
    mySourceProvider = sourceProvider;
  }

  @Override
  public String getCoverageDataFilePath() {
    return myFile.toString();
  }

  @Override
  public boolean ensureFileExists() {
    return Files.exists(myFile);
  }

  public String getSourceProvider() {
    return mySourceProvider;
  }

  @Override
  public boolean isValid() {
    return ensureFileExists();
  }
}
