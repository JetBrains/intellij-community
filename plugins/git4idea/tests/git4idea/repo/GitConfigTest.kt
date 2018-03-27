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
package git4idea.repo

import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.containers.ContainerUtil.getFirstItem
import git4idea.GitLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.test.GitPlatformTest
import git4idea.test.createRepository
import git4idea.test.git
import java.io.File
import java.util.*

class GitConfigTest : GitPlatformTest() {

  fun testRemotes() {
    val objects = loadRemotes()
    for (spec in objects) {
      doTestRemotes(spec.name, spec.config, spec.result)
    }
  }

  fun testBranches() {
    val objects = loadBranches()
    for (spec in objects) {
      doTestBranches(spec.name, spec.config, spec.result)
    }
  }

  //inspired by IDEA-135557
  fun `test branch with hash symbol`() {
    createRepository()
    addRemote("http://example.git")
    git("update-ref refs/remotes/origin/a#branch HEAD")
    git("branch --track a#branch origin/a#branch")

    val gitDir = File(projectPath, ".git")
    val config = GitConfig.read(File(gitDir, "config"))
    val dir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(gitDir)
    val reader = GitRepositoryReader(GitRepositoryFiles.getInstance(dir!!))
    val state = reader.readState(config.parseRemotes())
    val trackInfos = config.parseTrackInfos(state.localBranches.keys, state.remoteBranches.keys)
    assertTrue("Couldn't find correct a#branch tracking information among: [$trackInfos]",
               trackInfos.any { it.localBranch.name == "a#branch" && it.remoteBranch.nameForLocalOperations == "origin/a#branch" })
  }

  // IDEA-143363 Check that remote.pushdefault (generic, without remote name) doesn't fail the config parsing procedure
  fun `test remote unspecified section`() {
    createRepository()
    addRemote("git@github.com:foo/bar.git")
    git("config remote.pushdefault origin")

    assertSingleRemoteInConfig()
  }

  fun `test invalid section with remote prefix is ignored`() {
    createRepository()
    addRemote("git@github.com:foo/bar.git")
    git("config remote-cfg.newkey newval")

    assertSingleRemoteInConfig()
  }

  fun `test config options are case insensitive`() {
    createRepository()
    addRemote("git@github.com:foo/bar.git")
    val pushUrl = "git@github.com:foo/push.git"
    git("config remote.origin.pushurl " + pushUrl)

    val config = readConfig()
    val remote = getFirstItem(config.parseRemotes())
    assertNotNull(remote)
    assertSameElements("pushurl parsed incorrectly", remote!!.pushUrls, listOf(pushUrl))
  }

  fun `test config values are case sensitive`() {
    createRepository()
    val url = "git@GITHUB.com:foo/bar.git"
    addRemote(url)

    val config = readConfig()
    val remote = getFirstItem(config.parseRemotes())
    assertNotNull(remote)
    assertSameElements(remote!!.urls, listOf(url))
  }

  fun `test config sections are case insensitive`() {
    createRepository()
    addRemote("git@github.com:foo/bar.git")
    val configFile = configFile()
    FileUtil.writeToFile(configFile, FileUtil.loadFile(configFile).replace("remote", "REMOTE"))

    assertSingleRemoteInConfig()
  }

  fun `test config section values are case sensitive`() {
    createRepository()
    val expectedName = "ORIGIN"
    addRemote(expectedName, "git@github.com:foo/bar.git")

    val config = readConfig()
    val remote = getFirstItem(config.parseRemotes())
    assertNotNull(remote)
    assertEquals("Remote name is incorrect", expectedName, remote!!.name)
  }

  private fun createRepository(): GitRepository {
    return createRepository(myProject, projectPath, true)
  }

  private fun readConfig(): GitConfig {
    return GitConfig.read(configFile())
  }

  private fun assertSingleRemoteInConfig() {
    val remotes = readConfig().parseRemotes()
    assertSingleRemote(remotes)
  }

  private fun doTestRemotes(testName: String, configFile: File, resultFile: File) {
    val config = GitConfig.read(configFile)
    VcsTestUtil.assertEqualCollections(testName, config.parseRemotes(), readRemoteResults(resultFile))
  }

  private fun configFile(): File {
    val gitDir = File(projectPath, ".git")
    return File(gitDir, "config")
  }

  private fun doTestBranches(testName: String, configFile: File, resultFile: File) {
    val expectedInfos = readBranchResults(resultFile)
    val localBranches = expectedInfos.map { it.localBranch }
    val remoteBranches = expectedInfos.map { it.remoteBranch }

    VcsTestUtil.assertEqualCollections(testName, GitConfig.read(configFile).parseTrackInfos(localBranches, remoteBranches), expectedInfos)
  }

  private fun loadRemotes() = loadConfigData(getTestDataFolder("remote"))

  private fun loadBranches() = loadConfigData(getTestDataFolder("branch"))

  private class TestSpec(internal var name: String, internal var config: File, internal var result: File)

  private fun addRemote(url: String) {
    addRemote("origin", url)
  }

  private fun addRemote(name: String, url: String) {
    git("remote add $name $url")
  }

  private fun assertSingleRemote(remotes: Collection<GitRemote>) {
    assertEquals("Number of remotes is incorrect", 1, remotes.size)
    val remote = getFirstItem(remotes)
    assertNotNull(remote)
    assertEquals("origin", remote!!.name)
    assertEquals("git@github.com:foo/bar.git", remote.firstUrl)
  }

  private fun getTestDataFolder(subfolder: String): File {
    val pluginRoot = File(PluginPathManager.getPluginHomePath("git4idea"))
    val testData = File(pluginRoot, "testData")
    return File(File(testData, "config"), subfolder)
  }

  private fun loadConfigData(dataFolder: File): Collection<TestSpec> {
    val tests = dataFolder.listFiles { _, name -> !name.startsWith(".") }
    val data = mutableListOf<TestSpec>()
    for (testDir in tests) {
      var descriptionFile: File? = null
      var configFile: File? = null
      var resultFile: File? = null
      val files = testDir.listFiles()
      assertNotNull("No test specifications found in " + testDir.path, files)
      for (file in files!!) {
        when {
          file.name.endsWith("_desc.txt") -> descriptionFile = file
          file.name.endsWith("_config.txt") -> configFile = file
          file.name.endsWith("_result.txt") -> resultFile = file
        }
      }
      val message = " file not found in $testDir among ${Arrays.toString(testDir.list())}"
      assertNotNull("description $message", descriptionFile)
      assertNotNull("config $message", configFile)
      assertNotNull("result $message", resultFile)

      val testName = FileUtil.loadFile(descriptionFile!!).lines()[0] // description is in the first line of the desc-file
      if (!testName.toLowerCase().startsWith("ignore")) {
        data.add(TestSpec(testName, configFile!!, resultFile!!))
      }
    }
    return data
  }

  private fun readBranchResults(file: File): Collection<GitBranchTrackInfo> {
    val content = FileUtil.loadFile(file)
    val remotes = ArrayList<GitBranchTrackInfo>()
    val remStrings = StringUtil.split(content, "BRANCH")
    for (remString in remStrings) {
      if (StringUtil.isEmptyOrSpaces(remString)) {
        continue
      }
      val info = StringUtil.splitByLines(remString.trim { it <= ' ' })
      val branch = info[0]
      val remote = getRemote(info[1])
      val remoteBranchAtRemote = info[2]
      val remoteBranchHere = info[3]
      val merge = info[4] == "merge"
      remotes.add(GitBranchTrackInfo(GitLocalBranch(branch), GitStandardRemoteBranch(remote, remoteBranchAtRemote), merge))
    }
    return remotes
  }

  private fun getRemote(remoteString: String): GitRemote {
    val remoteInfo = remoteString.split(" ")
    return GitRemote(remoteInfo[0], getSingletonOrEmpty(remoteInfo, 1), getSingletonOrEmpty(remoteInfo, 2),
                     getSingletonOrEmpty(remoteInfo, 3), getSingletonOrEmpty(remoteInfo, 4))
  }

  private fun readRemoteResults(resultFile: File): Set<GitRemote> {
    val content = FileUtil.loadFile(resultFile)
    val remotes = mutableSetOf<GitRemote>()
    for (remString in content.split("REMOTE")) {
      if (StringUtil.isEmptyOrSpaces(remString)) {
        continue
      }
      val info = StringUtil.splitByLines(remString.trim { it <= ' ' })
      val name = info[0]
      val urls = info[1].split(" ")
      val pushUrls = info[2].split(" ")
      val fetchSpec = info[3].split(" ")
      val pushSpec = info[4].split(" ")
      remotes.add(GitRemote(name, urls, pushUrls, fetchSpec, pushSpec))
    }
    return remotes
  }

  private fun getSingletonOrEmpty(array: List<String>, i: Int) = if (array.size < i + 1) emptyList() else listOf(array[i])
}
