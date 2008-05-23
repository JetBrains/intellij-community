package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.openapi.vcs.RepositoryLocation;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class CvsRepositoryLocation implements RepositoryLocation {
  private VirtualFile myRootFile;
  private CvsEnvironment myEnvironment;
  private String myModuleName;

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
}
