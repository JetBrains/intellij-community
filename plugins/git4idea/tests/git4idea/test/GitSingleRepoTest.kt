/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vcs.VcsConfiguration
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_ACTION_SILENTLY
import com.intellij.openapi.vcs.VcsShowConfirmationOption.Value.DO_NOTHING_SILENTLY
import com.intellij.openapi.vcs.VcsTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.vcs.AbstractVcsTestCase
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.repo.GitRepository
import java.nio.file.Files
import java.nio.file.Paths

abstract class GitSingleRepoTest : GitPlatformTest() {

  protected lateinit var myRepo: GitRepository

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()
    myRepo = createRepository(myProject, myProjectPath, makeInitialCommit())
    cd(myProjectPath)
  }

  protected open fun makeInitialCommit() = true

  protected fun VcsConfiguration.StandardConfirmation.doSilently() =
    AbstractVcsTestCase.setStandardConfirmation(myProject, GitVcs.NAME, this, DO_ACTION_SILENTLY)

  protected fun VcsConfiguration.StandardConfirmation.doNothing() =
    AbstractVcsTestCase.setStandardConfirmation(myProject, GitVcs.NAME, this, DO_NOTHING_SILENTLY)

  protected fun prepareUnversionedFile(filePath: String): VirtualFile {
    val path = Paths.get(myProjectPath, filePath)
    Files.createDirectories(path.parent)
    Files.createFile(path)

    FileUtil.writeToFile(path.toFile(), "initial\ncontent\n")

    val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(path.toFile())!!
    updateChangeListManager()
    assertUnversioned(file)
    return file
  }

  protected fun VirtualFile.createDir(dir: String) = VcsTestUtil.findOrCreateDir(myProject, this, dir)!!

  protected fun VirtualFile.createFile(fileName: String, content: String = Math.random().toString()) =
    VcsTestUtil.createFile(myProject, this, fileName, content)!!

  protected fun renameFile(file: VirtualFile, newName: String) {
    VcsTestUtil.renameFileInCommand(myProject, file, newName)
    updateChangeListManager()
  }

  protected fun build(f: RepoBuilder.() -> Unit) = build(myRepo, f)

  protected fun assertUnversioned(file: VirtualFile) {
    assertTrue("File should be unversioned! All changes: " + GitUtil.getLogString(myProjectPath, changeListManager.allChanges),
               changeListManager.isUnversioned(file))
  }
}
