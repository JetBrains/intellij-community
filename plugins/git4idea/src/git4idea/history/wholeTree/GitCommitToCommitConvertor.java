/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.Convertor;
import git4idea.history.browser.GitHeavyCommit;

import java.util.Set;

/**
* @author irengrig
*/
public class GitCommitToCommitConvertor implements Convertor<GitHeavyCommit, CommitHashPlusParents> {
  private final static GitCommitToCommitConvertor ourInstance = new GitCommitToCommitConvertor();

  public static GitCommitToCommitConvertor getInstance() {
    return ourInstance;
  }

  @Override
  public CommitHashPlusParents convert(GitHeavyCommit o) {
    final Set<String> parentsHashes = o.getParentsHashes();
    return new CommitHashPlusParents(o.getShortHash(), ArrayUtil.toStringArray(parentsHashes), o.getDate().getTime(),
                                     o.getAuthor());
  }
}
