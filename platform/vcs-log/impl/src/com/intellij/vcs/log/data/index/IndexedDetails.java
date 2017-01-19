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
package com.intellij.vcs.log.data.index;

import com.intellij.vcs.log.data.LoadingDetails;
import com.intellij.vcs.log.data.VcsLogStorage;
import org.jetbrains.annotations.NotNull;

public class IndexedDetails extends LoadingDetails {
  @NotNull private final VcsLogIndex myIndex;
  private final int myCommitIndex;

  public IndexedDetails(@NotNull VcsLogIndex index,
                        @NotNull VcsLogStorage storage,
                        int commitIndex,
                        long loadingTaskIndex) {
    super(() -> storage.getCommitId(commitIndex), loadingTaskIndex);
    myIndex = index;
    myCommitIndex = commitIndex;
  }

  @NotNull
  @Override
  public String getFullMessage() {
    String message = myIndex.getFullMessage(myCommitIndex);
    if (message != null) return message;
    return super.getFullMessage();
  }

  @NotNull
  @Override
  public String getSubject() {
    String message = myIndex.getFullMessage(myCommitIndex);
    if (message != null) {
      return getSubject(message);
    }
    return super.getSubject();
  }

  @NotNull
  public static String getSubject(@NotNull String fullMessage) {
    int subjectEnd = fullMessage.indexOf("\n\n");
    if (subjectEnd > 0) return fullMessage.substring(0, subjectEnd).replace("\n", " ");
    return fullMessage.replace("\n", " ");
  }
}
