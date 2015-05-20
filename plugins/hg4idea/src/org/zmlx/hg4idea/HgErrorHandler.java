// Copyright 2010 Victor Iacoban
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
package org.zmlx.hg4idea;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgErrorUtil;

public abstract class HgErrorHandler {
  public static HgCommandResult ensureSuccess(@Nullable HgCommandResult result) throws VcsException {
    if (result == null) {
      throw new VcsException("Couldn't execute Mercurial command");
    }
    // workaround for mercurial: trying to merge with ancestor is not important/fatal error but natively hg produces abort error.
    if (fatalErrorOccurred(result) && !HgErrorUtil.isAncestorMergeError(result)) {
      throw new VcsException(result.getRawError());
    }
    return result;
  }

  private static boolean fatalErrorOccurred(@NotNull HgCommandResult result) {
    return result.getExitValue() == 255 || result.getRawError().contains("** unknown exception encountered");
  }
}