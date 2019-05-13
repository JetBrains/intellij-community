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
package com.intellij.cvsSupport2.cvsoperations.cvsCheckOut;

import com.intellij.cvsSupport2.application.CvsEntriesManager;
import com.intellij.cvsSupport2.config.CvsConfiguration;
import com.intellij.cvsSupport2.cvsoperations.common.CompositeOperation;
import com.intellij.openapi.vcs.FilePath;

/**
 * author: lesya
 */
public class CheckoutFilesOperation extends CompositeOperation {

  public CheckoutFilesOperation(FilePath[] files, CvsConfiguration cvsConfiguration) {
    for (FilePath file : files) {
      addFile(file, cvsConfiguration);
    }
  }

  private void addFile(FilePath file, CvsConfiguration cvsConfiguration) {
    final CheckoutFileOperation operation =
      new CheckoutFileOperation(file.getVirtualFileParent(), cvsConfiguration, file.getName(),
                                CvsEntriesManager.getInstance().getEntryFor(file.getVirtualFileParent(), file.getName()),
                                cvsConfiguration.MAKE_NEW_FILES_READONLY, file.isDirectory());
    addOperation(operation);
  }
}
