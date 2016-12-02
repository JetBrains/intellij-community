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
package git4idea.repo

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.branch.GitBranchUtil.stripRefsPrefix
import git4idea.test.GitExecutor.cd
import git4idea.test.GitExecutor.git
import git4idea.test.GitPlatformTest
import git4idea.test.GitTestUtil.makeCommit
import java.io.File

class GitRepositoryReaderNew2Test : GitPlatformTest() {

  fun `test packed symbolic ref`() {
    cd(myProjectPath)
    git("init")
    makeCommit("file.txt")
    git("pack-refs --all")

    // verify master is packed
    val gitDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(myProjectPath, ".git"))!!
    val repoFiles = GitRepositoryFiles.getInstance(gitDir)

    assertFalse("setup failed: master is not packed", File(repoFiles.refsHeadsFile, "master").exists())
    assertTrue("setup failed: master is not packed",
        FileUtil.loadFile(repoFiles.packedRefsPath).lines().any { it.endsWith("refs/heads/master") })

    val repo = GitRepositoryImpl.getInstance(myProjectRoot, myProject, false)
    assertEquals("HEAD should be on master", "master", stripRefsPrefix(repo.currentBranchName!!))
    assertEquals("HEAD revision is incorrect", git("rev-parse HEAD"), repo.currentRevision)
    assertEquals("master revision is incorrect",
        git("rev-parse master"),
        repo.branches.getHash(repo.branches.findLocalBranch("master")!!)!!.asString())
  }
}