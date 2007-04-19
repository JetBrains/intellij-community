package com.intellij.cvsSupport2.changeBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.openapi.vcs.RepositoryLocation;

/**
 * @author yole
 */
public class CvsRepositoryLocation implements RepositoryLocation {
  private CvsEnvironment myEnvironment;
  private String myModuleName;

  public CvsRepositoryLocation(final CvsEnvironment environment, final String moduleName) {
    myEnvironment = environment;
    myModuleName = moduleName;
  }

  public CvsEnvironment getEnvironment() {
    return myEnvironment;
  }

  public String getModuleName() {
    return myModuleName;
  }

  public String toString() {
    return myEnvironment + "|" + myModuleName;
  }

  public String toPresentableString() {
    return myModuleName;
  }
}
