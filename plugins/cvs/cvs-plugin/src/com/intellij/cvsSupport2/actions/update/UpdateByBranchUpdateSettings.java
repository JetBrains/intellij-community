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
package com.intellij.cvsSupport2.actions.update;

import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.RevisionOrDate;
import com.intellij.cvsSupport2.cvsoperations.dateOrRevision.SimpleRevision;
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

/**
 * author: lesya
 */
public class UpdateByBranchUpdateSettings implements UpdateSettings{
  private final String myBranchName;
  private final boolean myMakeNewFilesReadOnly;

  public UpdateByBranchUpdateSettings(String branchName, boolean makeNewFilesReadOnly) {
    myBranchName = branchName;
    myMakeNewFilesReadOnly = makeNewFilesReadOnly;
  }

  public boolean getPruneEmptyDirectories() {
    return true;
  }

  public String getBranch1ToMergeWith() {
    return null;
  }

  public String getBranch2ToMergeWith() {
    return null;
  }

  public boolean getResetAllSticky() {
    return false;
  }

  public boolean getDontMakeAnyChanges() {
    return false;
  }

  public boolean getCreateDirectories() {
    return true;
  }

  public boolean getCleanCopy() {
    return false;
  }

  public KeywordSubstitution getKeywordSubstitution() {
    return null;
  }

  public RevisionOrDate getRevisionOrDate() {
    return new SimpleRevision(myBranchName);
  }

  public boolean getMakeNewFilesReadOnly() {
    return myMakeNewFilesReadOnly;
  }
}
