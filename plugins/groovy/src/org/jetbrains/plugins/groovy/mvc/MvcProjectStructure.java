/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.jetbrains.plugins.groovy.mvc;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Collection;

/**
 * @author peter
 */
public abstract class MvcProjectStructure {
  protected final Module myModule;
  private final boolean myAuxModule;
  private final String myUserHomeSdkRoot;
  private final String mySdkWorkDir;

  public MvcProjectStructure(Module module, boolean auxModule, String userHomeSdkRoot, final File sdkWorkDir) {
    myAuxModule = auxModule;
    myModule = module;
    myUserHomeSdkRoot = userHomeSdkRoot;
    final VirtualFile sdkWorkDirVFile = MvcModuleStructureUtil.refreshAndFind(sdkWorkDir);
    mySdkWorkDir = sdkWorkDirVFile == null ? "" : sdkWorkDirVFile.getPath() + "/";
  }


  public boolean isValidContentRoot(@NotNull VirtualFile file) {
    if (file.getPath().startsWith(myUserHomeSdkRoot)) {
      if (!myAuxModule) {
        return false;
      }
      if (!file.getPath().startsWith(mySdkWorkDir)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  public abstract String getUserLibraryName();

  public abstract String[] getSourceFolders();
  public abstract String[] getTestFolders();
  public abstract String[] getInvalidSourceFolders();
  public abstract String[] getExcludedFolders();

  public void setupFacets(Collection<Consumer<ModifiableRootModel>> actions, Collection<VirtualFile> roots) {

  }
}
