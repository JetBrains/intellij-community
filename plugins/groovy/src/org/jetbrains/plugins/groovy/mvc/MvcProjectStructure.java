/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.mvc;

import com.intellij.facet.ModifiableFacetModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author peter
 */
public abstract class MvcProjectStructure {
  protected final Module myModule;
  private final boolean myAuxModule;
  private final String myUserHomeSdkRoot;
  private final String mySdkWorkDirPath;

  public MvcProjectStructure(Module module, boolean auxModule, String userHomeSdkRoot, final File sdkWorkDir) {
    myAuxModule = auxModule;
    myModule = module;
    myUserHomeSdkRoot = userHomeSdkRoot;
    final VirtualFile sdkWorkDirVFile = MvcModuleStructureUtil.refreshAndFind(sdkWorkDir);
    mySdkWorkDirPath = sdkWorkDirVFile == null ? "" : sdkWorkDirVFile.getPath() + "/";
  }

  public boolean isValidContentRoot(@NotNull VirtualFile file) {
    if (file.getPath().startsWith(myUserHomeSdkRoot)) {
      if (!myAuxModule) {
        return false;
      }
      if (!file.getPath().startsWith(mySdkWorkDirPath)) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  public abstract String getUserLibraryName();

  public abstract MultiMap<JpsModuleSourceRootType<?>, String> getSourceFolders();

  public abstract String[] getInvalidSourceFolders();

  public abstract String[] getExcludedFolders();

  public List<VirtualFile> getExcludedFolders(@NotNull VirtualFile root) {
    List<VirtualFile> res = new ArrayList<>();

    for (final String excluded : getExcludedFolders()) {
      VirtualFile dir = root.findFileByRelativePath(excluded);
      if (dir != null) {
        res.add(dir);
      }
    }

    return res;
  }

  public void setupFacets(Collection<Consumer<ModifiableFacetModel>> actions, Collection<VirtualFile> roots) {

  }
}
