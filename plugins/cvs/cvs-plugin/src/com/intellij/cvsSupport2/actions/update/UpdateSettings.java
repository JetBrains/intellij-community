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
import org.netbeans.lib.cvsclient.command.KeywordSubstitution;

/**
 * author: lesya
 */
public interface UpdateSettings {
  boolean getPruneEmptyDirectories();
  String getBranch1ToMergeWith();
  String getBranch2ToMergeWith();
  boolean getResetAllSticky();
  boolean getDontMakeAnyChanges();
  boolean getCreateDirectories();
  boolean getCleanCopy();
  RevisionOrDate getRevisionOrDate();
  KeywordSubstitution getKeywordSubstitution();
  boolean getMakeNewFilesReadOnly();

  UpdateSettings DONT_MAKE_ANY_CHANGES = new UpdateSettings(){
    @Override
    public boolean getPruneEmptyDirectories() {
      return false;
    }

    @Override
    public String getBranch1ToMergeWith() {
      return null;
    }

    @Override
    public boolean getResetAllSticky() {
      return false;
    }

    @Override
    public boolean getDontMakeAnyChanges() {
      return true;
    }

    @Override
    public String getBranch2ToMergeWith() {
      return null;
    }

    @Override
    public boolean getCreateDirectories() {
      return true;
    }

    @Override
    public boolean getCleanCopy() {
      return false;
    }

    @Override
    public KeywordSubstitution getKeywordSubstitution() {
      return null;
    }

    @Override
    public boolean getMakeNewFilesReadOnly() {
      return false;
    }

    @Override
    public RevisionOrDate getRevisionOrDate() {
      return RevisionOrDate.EMPTY;
    }
  };
}
