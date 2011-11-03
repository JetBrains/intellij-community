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
package git4idea.push;

import com.intellij.openapi.diagnostic.Logger;
import git4idea.repo.GitRemote;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Kirill Likhodedov
 */
public class GitPushSpec {

  //public static final GitPushSpec DEFAULT = new GitPushSpec(null, "");

  private static final Logger LOG = Logger.getInstance(GitPushSpec.class);
  
  @Nullable private final GitRemote myRemote;
  @NotNull private final String myRefspec;

  public GitPushSpec(@Nullable GitRemote remote, @NotNull String refspec) {
    myRemote = remote;
    myRefspec = refspec;
    verifyParams();
  }

  private void verifyParams() {
    if (myRemote == null) {
      LOG.assertTrue(myRefspec.isEmpty(), "If remote is not set, refspec should be empty. Refspec: " + myRefspec);
    }
  }

  //public Map<GitRepository, Collection<GitBranch>> parseAsRepositoriesAndBranches(Collection<GitRepository> repositories) {
  //  Map<GitRepository, Collection<GitBranch>> result = new HashMap<GitRepository, Collection<GitBranch>>();
  //  if (myRemote == null) {
  //    // from man: Works like git push <remote>, where <remote> is the current branch's remote (or origin, if no remote is configured for the current branch).
  //    // actually this is not true: if no remote is configured, then nothing will be pushed, except for the initial state of the repo, when master is pushed.
  //    for (GitRepository repository : repositories) {
  //      for (GitBranch branch : repository.getBranches().getLocalBranches()) {
  //        GitBranch tracked = branch.tracked(repository.getProject(), repository.getRoot());
  //        if (tracked != null) {
  //
  //        }
  //      }
  //    }
  //  } else {
  //
  //  }
  //}

  public boolean isSimple() {
    return myRemote == null;
  }

  @Nullable
  public GitRemote getRemote() {
    return myRemote;
  }


  @NotNull
  public String getRefspec() {
    return myRefspec;
  }
}
