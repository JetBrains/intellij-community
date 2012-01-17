/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.process;

import com.intellij.openapi.project.Project;
import git4idea.GitUtil;
import git4idea.merge.GitConflictResolver;
import git4idea.repo.GitRepository;

import java.util.Collection;

/**
 * @author Kirill Likhodedov
 */
class GitBranchUtil {
  static GitBranchOperationResult proposeToResolveUnmergedFiles(Project project,
                                                                Collection<GitRepository> repositories,
                                                                String errorTitle, String errorDescription) {
    // we have to search and display unmerged files for this root, but let it search all roots to solve any possible problems in other roots.
    GitConflictResolver gitConflictResolver = GitBranchOperationsProcessor.prepareConflictResolverForUnmergedFilesBeforeCheckout(project,
                                                                                                                                 GitUtil
                                                                                                                                   .getRoots(
                                                                                                                                     repositories));
    boolean res = gitConflictResolver.merge(); // try again to checkout this repository
    if (res) {
      return GitBranchOperationResult.success();
    }
    else {
      return GitBranchOperationResult.error(errorTitle, errorDescription);
    }
  }
}
