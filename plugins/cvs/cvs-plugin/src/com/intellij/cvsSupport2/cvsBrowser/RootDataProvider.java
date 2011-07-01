/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.cvsoperations.common.CompositeOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetModulesListOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.DirectoryContent;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.GetDirectoriesListViaUpdateOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsContent.*;

class RootDirectoryContentProvider extends CompositeOperation implements DirectoryContentProvider{
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
