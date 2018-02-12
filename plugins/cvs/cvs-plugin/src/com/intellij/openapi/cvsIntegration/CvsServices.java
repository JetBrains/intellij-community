/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.cvsIntegration;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;

import java.io.File;
import java.io.IOException;

public abstract class CvsServices {
  public static CvsServices getInstance(){
    return ServiceManager.getService(CvsServices.class);
  }

  public abstract CvsModule[] chooseModules(Project project, boolean allowRootSelection,
                                            boolean allowMultipleSelection,
                                            boolean allowFilesSelection, String title, String selectModulePageTitle);

  public abstract CvsRepository[] getConfiguredRepositories();
  public abstract String getScrambledPasswordForPServerCvsRoot(String cvsRoot);
  public abstract boolean saveRepository(CvsRepository repository);
  public abstract void openInEditor(Project project, CvsModule cvsFile);
  public abstract byte[] getFileContent(Project project, CvsModule cvsFile) throws IOException;
  public abstract CvsResult checkout(String[] modules, File checkoutTo, String directory, boolean makeNewFilesReadOnly, boolean pruneEmptyDirectories, Object keywordSubstitution, Project project, CvsRepository repository);
}
