// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.repo

import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.openapi.vcs.VcsTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.containers.ContainerUtil.getFirstItem
import git4idea.GitLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.test.GitPlatformTest
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.tac
import java.io.File
import java.util.*

class GitConfigTest : GitPlatformTest() {
  private val HOOK_FAILURE_MESSAGE = "IJ_TEST_GIT_HOOK_FAILED"

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

    val rootFile = File(projectPath)
    val gitFile = File(projectPath, ".git")
    val config = GitConfig.read(File(gitFile, "config"))
    val rootDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(rootFile)
    val gitDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(gitFile)
    val reader = GitRepositoryReader(GitRepositoryFiles.createInstance(rootDir!!, gitDir!!))
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

  fun `test instead of case insensitive`() {
    createRepository()
    addRemote("https://github.com/:foo/bar.git")
    git("config url.git@github.com:.InsteaDof https://github.com/")
    val config = readConfig()
    val remote = config.parseRemotes().first()
    assertEquals(listOf("git@github.com::foo/bar.git"), remote.urls)
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

  fun `test relative hook path is extracted from config`() {
    val repo = createRepository()

    createHook(".githooks/pre-commit")
    repo.update()

    assertFalse(repo.info.hooksInfo.areCommitHooksAvailable)
    assertFalse(repo.info.hooksInfo.isPrePushHookAvailable)

    tac("file1.txt")

    git("config core.hooksPath .githooks/")
    repo.update()

    assertTrue(repo.info.hooksInfo.areCommitHooksAvailable)
    assertFalse(repo.info.hooksInfo.isPrePushHookAvailable)

    assertHookFailure {
      tac("file2.txt")
    }

    git("config core.hooksPath .githooks")
    repo.update()

    assertTrue(repo.info.hooksInfo.areCommitHooksAvailable)
    assertFalse(repo.info.hooksInfo.isPrePushHookAvailable)

    assertHookFailure {
      tac("file3.txt")
    }

    git("config core.hooksPath .githooks2")
    repo.update()

    assertFalse(repo.info.hooksInfo.areCommitHooksAvailable)
    assertFalse(repo.info.hooksInfo.isPrePushHookAvailable)

    tac("file4.txt")

    createHook(".githooks2/pre-push")
    repo.update()

    assertFalse(repo.info.hooksInfo.areCommitHooksAvailable)
    assertTrue(repo.info.hooksInfo.isPrePushHookAvailable)
  }

  fun `test absolute hook path is extracted from config`() {
    val repo = createRepository()

    val hookFile = createHook(".githooks/pre-commit")
    repo.update()

    assertFalse(repo.info.hooksInfo.areCommitHooksAvailable)
    assertFalse(repo.info.hooksInfo.isPrePushHookAvailable)

    tac("file1.txt")

    git("config core.hooksPath " + hookFile.parent)
    repo.update()

    assertTrue(repo.info.hooksInfo.areCommitHooksAvailable)
    assertFalse(repo.info.hooksInfo.isPrePushHookAvailable)

    assertHookFailure {
      tac("file2.txt")
    }
  }

  fun `test last hook path is extracted from config`() {
    val repo = createRepository()

    createHook(".githooks4/pre-commit")
    repo.update()

    assertFalse(repo.info.hooksInfo.areCommitHooksAvailable)
    assertFalse(repo.info.hooksInfo.isPrePushHookAvailable)

    tac("file1.txt")

    Executor.append(".git/config", """
      [core]
        hooksPath = .githooks1
        hooksPath = .githooks2
      [core]
        hooksPath = .githooks3
        hooksPath = .githooks4
    """.trimIndent())
    repo.update()

    assertTrue(repo.info.hooksInfo.areCommitHooksAvailable)
    assertFalse(repo.info.hooksInfo.isPrePushHookAvailable)

    assertHookFailure {
      tac("file2.txt")
    }
  }

  private fun createHook(hookPath: String): File {
    val hookFile = touch(hookPath,
                         "#!/bin/sh\n" +
                         "echo $HOOK_FAILURE_MESSAGE\n" +
                         "exit 1")
    hookFile.setExecutable(true)
    return hookFile
  }

  private fun assertHookFailure(task: () -> Unit) {
    try {
      task()
      throw AssertionError("Hook failure expected")
    }
    catch (e: IllegalStateException) {
      if (!e.message.orEmpty().contains(HOOK_FAILURE_MESSAGE)) {
        throw AssertionError("Hook failure expected", e)
      }
    }
  }

  private fun createRepository(): GitRepository {
    return createRepository(myProject, projectNioRoot, true)
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

    val trackInfos = GitConfig.read(configFile).parseTrackInfos(localBranches, remoteBranches)
    VcsTestUtil.assertEqualCollections(testName, trackInfos, expectedInfos)
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
