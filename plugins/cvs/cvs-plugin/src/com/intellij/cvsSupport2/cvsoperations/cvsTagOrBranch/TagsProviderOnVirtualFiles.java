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
package com.intellij.cvsSupport2.cvsoperations.cvsTagOrBranch;

import com.intellij.cvsSupport2.cvsoperations.common.CvsCommandOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.LocalPathIndifferentLogOperation;
import com.intellij.cvsSupport2.cvsoperations.cvsLog.LogOperation;
import com.intellij.openapi.vcs.FilePath;

import java.util.Collection;

/**
 * author: lesya
 */
public class TagsProviderOnVirtualFiles implements TagsProvider {
  private final Collection<? extends FilePath> myFiles;

  public TagsProviderOnVirtualFiles(Collection<? extends FilePath> files) {
    myFiles = files;
  }

  @Override
  public CvsCommandOperation getOperation() {
    boolean containsOneFile = containsOneFile();
    if (containsOneFile) {
      return new LocalPathIndifferentLogOperation(getFirstFile().getIOFile());
    }
    else {
      return new LogOperation(myFiles);
    }
  }

  private boolean containsOneFile() {
    if (myFiles.size() != 1) return false;
    return !getFirstFile().isDirectory();
  }

  private FilePath getFirstFile() {
    return myFiles.iterator().next();
  }
}
