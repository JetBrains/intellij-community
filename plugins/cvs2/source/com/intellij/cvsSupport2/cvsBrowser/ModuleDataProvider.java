package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContentProvider;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetModuleContentOperation;

/**
 * author: lesya
 */
public class ModuleDataProvider extends AbstractVcsDataProvider{
  public ModuleDataProvider(CvsEnvironment environment, boolean showFiles) {
    super(environment, showFiles, true);
  }

  public DirectoryContentProvider createDirectoryContentProvider(String path) {
    return new GetModuleContentOperation(myEnvironment, path);
  }

  protected AbstractVcsDataProvider getChildrenDataProvider() {
    return new FolderDataProvider(myEnvironment, myShowFiles, myShowModules);
  }
}
