package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CompositeOperaton;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetModulesListOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContent;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetDirectoriesListViaUpdateOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.*;

class RootDirectoryContentProvider extends CompositeOperaton implements DirectoryContentProvider{
  private final GetDirectoriesListViaUpdateOperation myDirectoryListOperation;
  private final GetModulesListOperation myModuleListOperation;

  public RootDirectoryContentProvider(CvsEnvironment env){
    myDirectoryListOperation = new GetDirectoriesListViaUpdateOperation(env, ".");
    myModuleListOperation = new GetModulesListOperation(env);

    addOperation(myDirectoryListOperation);
    addOperation(myModuleListOperation);
  }

  public DirectoryContent getDirectoryContent() {
    DirectoryContent result = new DirectoryContent();
    result.copyDataFrom(myDirectoryListOperation.getDirectoryContent());
    result.copyDataFrom(myModuleListOperation.getDirectoryContent());
    return result;
  }
}

public class RootDataProvider extends AbstractVcsDataProvider{

  public static RootDataProvider createTestInstance(CvsEnvironment environment){
    return new RootDataProvider(environment, true, true);
  }

  public RootDataProvider(CvsEnvironment environment, boolean showFiles, boolean showModules) {
    super(environment, showFiles, showModules);
  }

  protected AbstractVcsDataProvider getChildrenDataProvider() {
    return new FolderDataProvider(myEnvironment, myShowFiles, myShowModules);
  }

  public DirectoryContentProvider createDirectoryContentProvider(String path) {
    return new RootDirectoryContentProvider(myEnvironment);
  }

}
