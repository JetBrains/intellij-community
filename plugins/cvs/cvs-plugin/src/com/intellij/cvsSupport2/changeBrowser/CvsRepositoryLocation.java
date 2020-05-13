// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.openapi.vcs.RepositoryLocation;
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

  @Override
  public String toPresentableString() {
    return myModuleName;
  }

  @Override
  public String getKey() {
    return myModuleName;
  }
}
