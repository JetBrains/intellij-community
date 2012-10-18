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
package git4idea.test

import git4idea.repo.GitRepository

/**
 * Create popular scenarios used in multiple tests, for example:
 *  - create a branch and commit something there;
 *  - make some unmerged files in the working tree;
 *  - make the situation when local changes would be overwritten by merge.
 *
 * @author Kirill Likhodedov
 */
@Mixin(GitExecutor)
class GitScenarios {

  private static final String BRANCH_FOR_UNMERGED_CONFLICTS = "unmerged_files_branch_" + Math.random();

  static final def LOCAL_CHANGES_OVERWRITTEN_BY = [
          initial:    "common content\ncommon content\ncommon content\n",
          branchLine: "line with branch changes\n",
          masterLine: "line with master changes"
  ]

  /**
   * Create a branch with a commit and return back to master.
   */
  def branchWithCommit(GitRepository repository, String name, String file = "branch_file.txt", String content = "branch content") {
    cd repository
    git("checkout -b $name")
    touch(file, content)
    git("add $file")
    git("commit -m branch_content")

    git("checkout master")
  }

  /**
   * Create a branch with a commit and return back to master.
   */
  def branchWithCommit(Collection<GitRepository> repositories, String name,
                       String file = "branch_file.txt", String content = "branch content") {
    repositories.each { branchWithCommit(it, name, file, content) }
  }

  /**
   * Make an unmerged file in the repository.
   */
  def unmergedFiles(GitRepository repository) {
    cd repository
    touch("unmerged.txt", "initial content")
    git("add unmerged.txt")
    git("commit -m initial_content")

    git("checkout -b $BRANCH_FOR_UNMERGED_CONFLICTS")
    echo("unmerged.txt", "branch content")
    git("commit -am branch_content")

    git("checkout master")
    echo("unmerged.txt", "master content")
    git("commit -am master_content")

    git("merge $BRANCH_FOR_UNMERGED_CONFLICTS")
    git("branch -D $BRANCH_FOR_UNMERGED_CONFLICTS")
  }

  /**
   * Create an untracked file in master and a tracked file with the same name in the branch.
   * This produces the "some untracked files would be overwritten by..." error when trying to checkout or merge.
   * Branch with the given name shall exist.
   */
  def untrackedFileOverwrittenBy(GitRepository repository, String branch, Collection<String> fileNames) {
    cd repository
    git("checkout $branch")

    for (it in fileNames) {
      touch(it, "branch content")
      git("add $it")
    }

    git("commit -m untracked_files")
    git("checkout master")

    for (it in fileNames) {
      touch(it, "master content")
    }
  }

  /**
   * Creates a file in both master and branch so that the content differs, but can be merged without conflicts.
   * That way, git checkout/merge will fail with "local changes would be overwritten by checkout/merge",
   * but smart checkout/merge (stash-checkout/merge-unstash) would succeed without conflicts.
   *
   * NB: the branch should not exist before this is called!
   */
  def localChangesOverwrittenByWithoutConflict(GitRepository repository, String branch, Collection<String> fileNames) {
    cd repository

    for (it in fileNames) {
      echo(it, LOCAL_CHANGES_OVERWRITTEN_BY.initial)
      git("add $it")
    }
    git("commit -m initial_changes")

    git("checkout -b $branch")
    for (it in fileNames) {
      prepend(it, LOCAL_CHANGES_OVERWRITTEN_BY.branchLine)
      git("add $it")
    }
    git("commit -m branch_changes")

    git("checkout master")
    for (it in fileNames) {
      append(it, LOCAL_CHANGES_OVERWRITTEN_BY.masterLine)
    }
  }

  def append(String fileName, String content) {
     echo(fileName, content)
  }

  def prepend(String fileName, String content) {
    def previousContent = cat(fileName)
    new File(ourCurrentDir, fileName).withWriter("UTF-8") { it.write(content + previousContent) }
  }

}
