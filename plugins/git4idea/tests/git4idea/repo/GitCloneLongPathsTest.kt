// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.util.SystemInfo
import git4idea.commands.Git
import git4idea.test.GitSingleRepoTest
import git4idea.test.makeCommit
import org.junit.Assume
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

class GitCloneLongPathsTest : GitSingleRepoTest() {

  fun `test clone repo with long paths`() {
    Assume.assumeTrue(SystemInfo.isWindows)

    git("config core.longpaths true")
    val path = Path("a".repeat(100), "b".repeat(100), "c".repeat(100), "test.txt") // 260+
    makeCommit(path.pathString)

    val cloned = projectNioRoot.resolve("cloned")
    val cloneResult = Git.getInstance().clone(project,
                                              cloned.parent.toFile(),
                                              "file://${repo.root.path}",
                                              cloned.name)

    assertTrue(cloneResult.success())
  }
}