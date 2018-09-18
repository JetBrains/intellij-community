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
package git4idea.tests

import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.openapi.vcs.VcsTestUtil
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import git4idea.repo.GitRepository
import git4idea.test.*
import java.io.File

class GitChangeProviderNestedRepositoriesTest : GitSingleRepoTest() {
  private lateinit var dirtyScopeManager: VcsDirtyScopeManager

  @Throws(Exception::class)
  public override fun setUp() {
    super.setUp()
    dirtyScopeManager = VcsDirtyScopeManager.getInstance(project)
  }

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus(listOf("#com.intellij.openapi.vcs.changes", "#GitStatus"))

  // IDEA-149060
  fun `test changes in 3-level nested root`() {
    // 1. prepare roots and files
    val childRepo = createSubRoot(projectPath, "child")
    val grandChildRepo = createSubRoot(childRepo.root.path, "grand")

    createFileStructure(repo.root, "a.txt")
    createFileStructure(childRepo.root, "in1.txt", "in2.txt", "grand/inin1.txt", "grand/inin2.txt")
    cd(childRepo)
    addCommit("committed file structure")
    cd(grandChildRepo)
    addCommit("committed file structure")
    refresh()

    // 2. make changes and make sure they are recognized
    val atxt = getVirtualFile("a.txt")
    VcsTestUtil.editFileInCommand(project, atxt, "123")
    val in1 = getVirtualFile("child/in1.txt")
    VcsTestUtil.editFileInCommand(project, in1, "321")
    val in2 = getVirtualFile("child/in2.txt")
    VcsTestUtil.editFileInCommand(project, in2, "321*")
    val grin1 = getVirtualFile("child/grand/inin1.txt")
    VcsTestUtil.editFileInCommand(project, grin1, "321*")

    dirtyScopeManager.markEverythingDirty()
    changeListManager.ensureUpToDate(false)

    // 3. move changes
    val newList = changeListManager.addChangeList("new", "new")
    val change1 = changeListManager.getChange(in1)!!
    val change2 = changeListManager.getChange(in2)!!
    val change3 = changeListManager.getChange(grin1)!!
    changeListManager.moveChangesTo(newList, change1, change2, change3)

    dirtyScopeManager.filesDirty(listOf(in1), listOf(repo.root))
    changeListManager.ensureUpToDate(false)

    // 4. check that changes are in correct changelists
    val list = changeListManager.getChangeList(in1)!!
    assertEquals("new", list.name)
    val list2 = changeListManager.getChangeList(in2)!!
    assertEquals("new", list2.name)

    val list3 = changeListManager.getChangeList(grin1)
    assertNotNull("Change for ${VfsUtil.getRelativePath(grin1, projectRoot)} not found", list3)
    assertEquals("new", list3!!.name)
  }

  private fun createSubRoot(parent: String, name: String) : GitRepository {
    val childRoot = File(parent, name)
    assertTrue(childRoot.mkdir())
    val repo = createRepository(project, childRoot.path)
    Executor.cd(parent)
    touch(".gitignore", "child")
    addCommit("gitignore")
    return repo
  }

  private fun getVirtualFile(relativePath: String) : VirtualFile {
    return VfsUtil.findFileByIoFile(File(projectPath, relativePath), true)!!
  }
}
