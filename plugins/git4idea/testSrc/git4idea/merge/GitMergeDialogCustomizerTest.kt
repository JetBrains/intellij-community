// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.merge

import com.intellij.testFramework.junit5.TestApplication
import git4idea.test.GitSingleRepoContext
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.prepareConflict
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitMergeDialogCustomizerTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  // IJPL-253755: the merged branch name must survive another branch that shares the merged commit.
  @Test
  fun `test merged branch name resolves when another branch shares the commit`(): Unit = with(context) {
    repo.prepareConflict(initialBranch = "master", featureBranch = "feature", conflictingFile = "c.txt")
    git("branch other master") // 'other' now points at the same commit as 'master'
    git("merge master", true) // conflicts on 'feature'
    repo.update()

    // Before the fix the shared commit made the lookup ambiguous and the label fell back to the short hash.
    assertThat(getSingleMergeBranchName(listOf(repo))).isEqualTo("master")
  }

  // IJPL-253755: the rebase onto branch must never be read from MERGE_MSG. During a rebase git writes the replayed
  // commit message to MERGE_MSG, so a branch quoted there is unrelated to the onto branch. This guards
  // resolveRebaseOntoBranch against gaining the MERGE_MSG tie-breaker that the merge path uses.
  @Test
  fun `test rebase onto branch is not resolved from merge message`(): Unit = with(context) {
    val f = file("f.txt")
    f.create("a\nb\nc\n").addCommit("base")
    git("checkout -b feature")
    f.write("a\nFEATURE\nc\n").addCommit("on_feature")
    git("checkout master")
    f.write("a\nMAIN\nc\n").addCommit("on_master")
    git("branch release master") // 'release' shares master's tip, which is the rebase onto
    git("checkout feature")
    git("rebase master", true) // conflict replaying 'on_feature' onto master
    // git writes the replayed commit message to MERGE_MSG. Seed the form git produces for a branch merge, because
    // the test git executor cannot put a single quote in a commit subject.
    repo.repositoryFiles.mergeMessageFile.writeText("Merge branch 'release'\n\n# Conflicts:\n#\tf.txt\n")
    repo.update()

    // 'release' appears only in MERGE_MSG. Reading it here would mislabel the onto branch as 'release'.
    val ontoLabel = getSingleMergeBranchName(listOf(repo))
    assertThat(ontoLabel).isNotNull()
    assertThat(ontoLabel).isNotEqualTo("release")
  }
}
