/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcs.log.Hash;
import com.intellij.vcs.log.VcsUser;
import com.intellij.vcs.log.impl.VcsChangesLazilyParsedDetails;
import git4idea.history.GitChangesParser;
import git4idea.history.GitLogStatusInfo;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * Represents a Git commit with its meta information (hash, author, message, etc.), its parents and the {@link Change changes}.
 *
 * @author Kirill Likhodedov
 */
public final class GitCommit extends VcsChangesLazilyParsedDetails {

  public GitCommit(Project project, @NotNull Hash hash, @NotNull List<Hash> parents, long commitTime, @NotNull VirtualFile root,
                   @NotNull String subject, @NotNull VcsUser author, @NotNull String message, @NotNull VcsUser committer,
                   long authorTime, @NotNull List<GitLogStatusInfo> reportedChanges) {
    super(hash, parents, commitTime, root, subject, author, message, committer, authorTime,
          new MyChangesComputable(new Data(project, root, reportedChanges, hash, commitTime, parents)));

  }

  private static class MyChangesComputable implements ThrowableComputable<Collection<Change>, VcsException> {

    private Data myData;
    private Collection<Change> myChanges;

    public MyChangesComputable(Data data) {
      myData = data;
    }

    @Override
    public Collection<Change> compute() throws VcsException {
      if (myChanges == null) {
        myChanges = GitChangesParser.parse(myData.project, myData.root, myData.changesOutput, myData.hash.asString(),
                                           new Date(myData.time), ContainerUtil.map(myData.parents, new Function<Hash, String>() {
            @Override
            public String fun(Hash hash) {
              return hash.asString();
            }
          }));
        myData = null; // don't hold the not-yet-parsed string
      }
      return myChanges;
    }

  }

  private static class Data {
    private final Project project;
    private final VirtualFile root;
    private final List<GitLogStatusInfo> changesOutput;
    private final Hash hash;
    private final long time;
    private final List<Hash> parents;

    public Data(Project project, VirtualFile root, List<GitLogStatusInfo> changesOutput, Hash hash, long time, List<Hash> parents) {
      this.project = project;
      this.root = root;
      this.changesOutput = changesOutput;
      this.hash = hash;
      this.time = time;
      this.parents = parents;
    }
  }

}
