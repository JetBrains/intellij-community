// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.provider.update;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;

import java.util.List;

public interface HgUpdater {

  void update(UpdatedFiles updatedFiles, ProgressIndicator indicator, List<VcsException> exceptions)
    throws VcsException;

  static class UpdateConfiguration{
    private boolean shouldPull = true;
    private boolean shouldUpdate = true;
    private boolean shouldMerge = true;
    private boolean shouldCommitAfterMerge = true;

    public void setShouldPull(boolean shouldPull) {
      this.shouldPull = shouldPull;
    }

    public void setShouldUpdate(boolean shouldUpdate) {
      this.shouldUpdate = shouldUpdate;
    }

    public void setShouldMerge(boolean shouldMerge) {
      this.shouldMerge = shouldMerge;
    }

    public void setShouldCommitAfterMerge(boolean shouldCommitAfterMerge) {
      this.shouldCommitAfterMerge = shouldCommitAfterMerge;
    }

    public boolean shouldPull() {
    return shouldPull;
  }
  
  public boolean shouldUpdate() {
    return shouldUpdate;
  }
  
  public boolean shouldMerge() {
    return shouldUpdate() && shouldMerge;
  }
  
  public boolean shouldCommitAfterMerge() {
    return shouldMerge() && shouldCommitAfterMerge;
  }
  }
}
