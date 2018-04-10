/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class CvsRepositoryLocation implements RepositoryLocation {
  private final VirtualFile myRootFile;
  private final CvsEnvironment myEnvironment;
  private final String myModuleName;

  public CvsRepositoryLocation(@Nullable final VirtualFile rootFile, final CvsEnvironment environment, final String moduleName) {
    myRootFile = rootFile;
    myEnvironment = environment;
    myModuleName = moduleName;
  }

  public CvsEnvironment getEnvironment() {
    return myEnvironment;
  }

  public String getModuleName() {
    return myModuleName;
  }

  @Nullable
  public VirtualFile getRootFile() {
    return myRootFile;
  }

  public String toString() {
    return myEnvironment.getCvsRootAsString() + "|" + myModuleName;
  }

  public String toPresentableString() {
    return myModuleName;
  }

  public String getKey() {
    return myModuleName;
  }

  @Override
  public void onBeforeBatch() {
  }

  @Override
  public void onAfterBatch() {
  }
}
