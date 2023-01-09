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
package git4idea.changes;

import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.vcs.CommittedChangeListForRevision;
import git4idea.GitRevisionNumber;
import git4idea.GitVcs;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Date;

public class GitCommittedChangeList extends CommittedChangeListForRevision {

  private final boolean myModifiable;
  private final AbstractVcs myVcs;

  public GitCommittedChangeList(@NotNull String name,
                                @NotNull String comment,
                                @NotNull String committerName,
                                @NotNull GitRevisionNumber revisionNumber,
                                @NotNull Date commitDate,
                                @NotNull Collection<Change> changes,
                                @NotNull GitVcs vcs,
                                boolean isModifiable) {
    super(name, comment, committerName, commitDate, changes, revisionNumber);
    myVcs = vcs;
    myModifiable = isModifiable;
  }

  @Override
  public boolean isModifiable() {
    return myModifiable;
  }

  @Override
  public long getNumber() {
    @NonNls String revisionNumber = getRevisionNumber().asString();
    // TODO: wrong braces?
    return Long.parseLong(revisionNumber.substring(0, 15), 16) << 4 + Integer.parseInt(revisionNumber.substring(15, 16), 16);
  }

  @Override
  @NotNull
  public GitRevisionNumber getRevisionNumber() {
    return (GitRevisionNumber)super.getRevisionNumber();
  }

  @Override
  public AbstractVcs getVcs() {
    return myVcs;
  }
}
