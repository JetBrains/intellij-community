// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.test

import com.intellij.openapi.vcs.Executor.append
import com.intellij.openapi.vcs.Executor.touch
import git4idea.repo.GitRepository
import java.io.File
import java.util.*

fun build(repo: GitRepository, f: RepoBuilder.() -> Unit) {
  val builder = RepoBuilder(repo)
  builder.f()
  builder.build()
}

class RepoBuilder(val repo: GitRepository) {
  init {
    cd(repo)
  }

  val master: String = "master"
  val feature: String = "feature"

  private val myCommitIndices = HashMap<Int, String>()
  private var myCurrentBranch : String = master

  operator fun String.invoke(commands: RepoBuilder.() -> Unit) { // switch branch
    createOrCheckout(this)
    myCurrentBranch = this
    commands()
  }

  private fun createOrCheckout(branch: String) {
    if (isFresh()) {
      if (branch != "master") {
        repo.git("checkout -b $branch")
      }
    }
    else {
      if (repo.git("branch").split("\n").map { it.replace("*", "").trim() }.contains(branch)) {
        repo.git("checkout $branch")
      }
      else {
        repo.git("checkout -b $branch")
      }
    }
  }

  private fun isFresh(): Boolean = myCommitIndices.isEmpty()

  operator fun String.invoke(fromCommit: Int, commands: RepoBuilder.() -> Unit) {
    repo.git("checkout -b $this ${myCommitIndices[fromCommit]}")
    myCurrentBranch = this
    commands()
  }

  operator fun Int.invoke(file: String = this.toString() + ".txt",
                          content: String = "More content in $myCurrentBranch: ${randomHash()}",
                          commitMessage: String = if (File(repo.root.path, file).exists()) "Created $file" else "Modified $file") {
    modifyAndCommit(this, file, content, commitMessage)
  }

  fun build() {
    repo.update()
  }

  private fun modifyAndCommit(id: Int, file: String, content: String, commitMessage: String) {
    if (File(repo.root.path, file).exists()) {
      append(file, content)
    }
    else {
      touch(file, content)
    }
    repo.git("add --verbose .")
    val hash = repo.commit(commitMessage)
    assert(!myCommitIndices.containsKey(id)) { "commit $id is already in the map!" }
    myCommitIndices.put(id, hash)
  }
}

fun randomHash() = Integer.toHexString(Random().nextInt())
