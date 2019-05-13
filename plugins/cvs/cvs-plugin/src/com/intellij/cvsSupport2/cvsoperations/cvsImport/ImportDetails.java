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
package com.intellij.cvsSupport2.cvsoperations.cvsImport;

import com.intellij.cvsSupport2.connections.CvsEnvironment;
import com.intellij.cvsSupport2.connections.CvsRootProvider;
import com.intellij.cvsSupport2.cvsoperations.common.CvsOperation;
import com.intellij.cvsSupport2.ui.experts.importToCvs.FileExtension;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.netbeans.lib.cvsclient.command.importcmd.ImportCommand;
import org.netbeans.lib.cvsclient.util.IIgnoreFileFilter;

import java.io.File;
import java.util.Collection;

/**
 * author: lesya
 */

public class ImportDetails {
  private final File myBaseImportDirectory;
  private final String myVendor;
  private final String myReleaseTag;
  private final String myLogMessage;
  private final String myModuleName;
  private final CvsEnvironment myEnvironment;
  private final Collection<? extends FileExtension> myWrappers;
  private final IIgnoreFileFilter myIgnoreFileFilter;

  public ImportDetails(@NotNull File baseImportDirectory,
                       String vendor,
                       String releaseTag,
                       String logMessage,
                       String moduleName,
                       CvsEnvironment env,
                       Collection<? extends FileExtension> wrappers,
                       IIgnoreFileFilter ignoreFileFilter) {
    myBaseImportDirectory = baseImportDirectory;
    myVendor = vendor;
    myReleaseTag = releaseTag;
    myLogMessage = logMessage;
    myModuleName = moduleName;
    myEnvironment = env;
    myWrappers = wrappers;
    myIgnoreFileFilter = ignoreFileFilter;
  }

  public void prepareCommand(ImportCommand result) {
    result.setVendorTag(myVendor);
    result.setReleaseTag(myReleaseTag);
    result.setModule(getModulePath());
    result.setLogMessage(myLogMessage);

    for (final FileExtension fileExtension : myWrappers) {
      result.addWrapper("*." + fileExtension.getExtension(), fileExtension.getKeywordSubstitution().getSubstitution());
    }
  }

  public int getTotalFilesInSourceDirectory() {
    return CvsOperation.calculateFilesIn(myBaseImportDirectory);
  }

  private String getModulePath() {
    final String relativePath = FileUtil.getRelativePath(myBaseImportDirectory.getAbsoluteFile().getParentFile(),
                                                         myBaseImportDirectory.getAbsoluteFile());
    return replaceBaseImportDirectoryNameToModuleNameIn(relativePath);
  }

  public String getModuleName() {
    return myModuleName;
  }

  private String replaceBaseImportDirectoryNameToModuleNameIn(String relativePath) {
    return myModuleName + relativePath.substring(myBaseImportDirectory.getName().length());
  }

  public File getBaseImportDirectory() {
    return myBaseImportDirectory;
  }

  public CvsRootProvider getCvsRoot() {
    return CvsRootProvider.createOn(myBaseImportDirectory, myEnvironment);
  }

  public IIgnoreFileFilter getIgnoreFileFilter() {
    return myIgnoreFileFilter;
  }
}
