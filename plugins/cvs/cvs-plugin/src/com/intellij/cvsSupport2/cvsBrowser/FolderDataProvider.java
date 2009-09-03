package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;

public class FolderDataProvider extends AbstractVcsDataProvider {
  public FolderDataProvider(CvsEnvironment environment, boolean showFiles, boolean showModules) {
    super(environment, showFiles, showModules);
  }

  protected AbstractVcsDataProvider getChildrenDataProvider() {
    return this;
  }

}
