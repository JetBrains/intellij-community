/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.cvsSupport2;

import com.intellij.cvsSupport2.changeBrowser.CvsRepositoryLocation;
import com.intellij.openapi.vcs.LocalFilePath;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public class CvsFilePath extends LocalFilePath {

  private final CvsRepositoryLocation myRepositoryLocation;

  public CvsFilePath(@NotNull String path, boolean isDirectory, @NotNull CvsRepositoryLocation repositoryLocation) {
    super(path, isDirectory);
    myRepositoryLocation = repositoryLocation;
  }

  @NotNull
  public CvsRepositoryLocation getRepositoryLocation() {
    return myRepositoryLocation;
  }
}
