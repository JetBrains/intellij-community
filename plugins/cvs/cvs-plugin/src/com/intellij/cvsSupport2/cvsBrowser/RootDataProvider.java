/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
import com.intellij.cvsSupport2.cvsoperations.cvsContent.*;
import com.intellij.util.Consumer;

class RootDirectoryContentProvider extends CompositeOperation implements DirectoryContentProvider{
  private final GetDirectoriesListViaUpdateOperation myDirectoryListOperation;
  private final GetModulesListOperation myModuleListOperation;

  RootDirectoryContentProvider(CvsEnvironment env){
    myDirectoryListOperation = new GetDirectoriesListViaUpdateOperation(env, ".");
    myModuleListOperation = new GetModulesListOperation(env);

    addOperation(myDirectoryListOperation);
    addOperation(myModuleListOperation);
  }

  @Override
  public DirectoryContent getDirectoryContent() {
    final DirectoryContent result = new DirectoryContent();
    result.copyDataFrom(myDirectoryListOperation.getDirectoryContent());
    result.copyDataFrom(myModuleListOperation.getDirectoryContent());
    return result;
  }

  @Override
  public void setStreamingListener(Consumer<DirectoryContent> streamingListener) {
    myDirectoryListOperation.setStreamingListener(streamingListener);
    myModuleListOperation.setStreamingListener(streamingListener);
  }
}
public class RootDataProvider extends AbstractVcsDataProvider{

  public static RootDataProvider createTestInstance(CvsEnvironment environment){
    return new RootDataProvider(environment);
  }

  public RootDataProvider(CvsEnvironment environment) {
    super(environment);
  }

  @Override
  public AbstractVcsDataProvider getChildrenDataProvider() {
    return new FolderDataProvider(myEnvironment);
  }

  @Override
  public DirectoryContentProvider createDirectoryContentProvider(String path) {
    return new RootDirectoryContentProvider(myEnvironment);
  }
}
