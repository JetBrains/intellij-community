// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.Executor
import com.intellij.openapi.vcs.Executor.touch
import com.intellij.openapi.vcs.VcsTestUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import git4idea.GitLocalBranch
import git4idea.GitStandardRemoteBranch
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.test.GitSingleRepoContext
import git4idea.test.TestDataUtil
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.tac
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.Locale

private const val HOOK_FAILURE_MESSAGE = "IJ_TEST_GIT_HOOK_FAILED"

@TestApplication
internal class GitConfigTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun testRemotes() {
    for (spec in loadConfigData(getTestDataFolder("remote"))) {
      doTestRemotes(spec.name, spec.config, spec.result)
    }
  }

  @Test
  fun testBranches() {
    for (spec in loadConfigData(getTestDataFolder("branch"))) {
      doTestBranches(spec.name, spec.config, spec.result)
    }
  }

  //inspired by IDEA-135557
  @Test
  fun `test branch with hash symbol`(): Unit = with(context) {
    addRemote("http://example.git")
    git("update-ref refs/remotes/origin/a#branch HEAD")
    git("branch --track a#branch origin/a#branch")

    val config = GitConfig.read(project, projectNioRoot)
    val rootDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(projectPath))
    val gitDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(projectPath, ".git"))
    val reader = GitRepositoryReader(project, GitRepositoryFiles.createInstance(rootDir!!, gitDir!!))
    val state = reader.readState(config.parseRemotes())
    val trackInfos = config.parseTrackInfos(state.localBranches.keys, state.remoteBranches.keys)
    assertThat(trackInfos)
      .describedAs("Couldn't find correct a#branch tracking information")
      .anyMatch { it.localBranch.name == "a#branch" && it.remoteBranch.nameForLocalOperations == "origin/a#branch" }
  }

  // IDEA-143363 Check that remote.pushdefault (generic, without remote name) doesn't fail the config parsing procedure
  @Test
  fun `test remote unspecified section`(): Unit = with(context) {
    addRemote("git@github.com:foo/bar.git")
    git("config remote.pushdefault origin")

    assertSingleRemoteInConfig()
  }

  @Test
  fun `test invalid section with remote prefix is ignored`(): Unit = with(context) {
    addRemote("git@github.com:foo/bar.git")
    git("config remote-cfg.newkey newval")

    assertSingleRemoteInConfig()
  }

  @Test
  fun `test config options are case insensitive`(): Unit = with(context) {
    addRemote("git@github.com:foo/bar.git")
    val pushUrl = "git@github.com:foo/push.git"
    git("config remote.origin.pushurl $pushUrl")

    val remote = readConfig().parseRemotes().firstOrNull()
    assertThat(remote).isNotNull()
    remote!!.checkRemoteUrls(listOf("git@github.com:foo/bar.git"), listOf(pushUrl))
  }

  @Test
  fun `test instead of case insensitive`(): Unit = with(context) {
    addRemote("https://github.com/:foo/bar.git")
    git("config url.git@github.com:.InsteaDof https://github.com/")

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("git@github.com::foo/bar.git"), listOf("git@github.com::foo/bar.git"))
  }

  @Test
  fun `test insteadOf resolving when pushInsteadOf is specified`(): Unit = with(context) {
    addRemote("test:group/bar.git")
    git("""config url.https://github.com/.insteadOf test:""")
    git("""config url.git@github.com:.pushInsteadOf test:""")

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("https://github.com/group/bar.git"), listOf("git@github.com:group/bar.git"))
  }

  @Test
  fun `test pushInsteadOf affects url substitution when declared before insteadOf`(): Unit = with(context) {
    addRemote("test:group/bar.git")
    git("""config url.git@github.com:.pushInsteadOf test:""")
    git("""config url.https://github.com/.insteadof test:""")

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("https://github.com/group/bar.git"), listOf("git@github.com:group/bar.git"))
  }

  @Test
  fun `test irrelevant pushInsteadOf with a placeholder doesn't affect URL-resolving of other placeholders`(): Unit = with(context) {
    addRemote("test:group/bar.git")
    git("""config url.git@github.com:.pushInsteadOf notTest:""")
    git("""config url.https://github.com/.insteadof test:""")

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("https://github.com/group/bar.git"), listOf("https://github.com/group/bar.git"))
  }

  @Test
  fun `test explicit pushUrl is substituted only with insteadOf`(): Unit = with(context) {
    addRemote("test:group/bar.git")
    git("""config remote.origin.pushurl test:group/push.git""")
    git("""config url.https://github.com/.insteadOf test:""")
    git("""config url.git@github.com:.pushInsteadOf test:""")

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("https://github.com/group/bar.git"), listOf("https://github.com/group/push.git"))
  }

  @Test
  fun `test pushUrls not set and longest insteadOf and pushInsteadOf placeholders are applied to urls`(): Unit = with(context) {
    addRemote("test:group/bar.git")
    git("""config url.https://github.com/.insteadOf test:""")
    git("""config url.https://github.com/special/.insteadOf test:group/""")
    git("""config url.https://github.com/gr/.insteadOf test:gr""")
    git("""config url.git@github.com:.pushInsteadOf test:""")
    git("""config url.ssh://git@github.com/push/group/.pushInsteadOf test:group/""")
    git("""config url.ssh://git@github.com/other/.pushInsteadOf test:gr""")

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("https://github.com/special/bar.git"), listOf("ssh://git@github.com/push/group/bar.git"))
  }

  @Test
  fun `test many explicit pushUrls are substituted only with insteadOf`(): Unit = with(context) {
    addRemote("test:group/bar.git")
    Executor.append(".git/config", """
      [remote "origin"]
        pushurl = test:group/push.git
        pushurl = test:group/push2.git
      [url "https://github.com/"]
	      insteadOf = test:      
      [url "https://github.com/special/"]
	      insteadOf = test:group/      
      [url "git@github.com:"]
	      pushInsteadOf = test:
    """.trimIndent())
    repo.update()

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("https://github.com/special/bar.git"),
                           listOf("https://github.com/special/push.git", "https://github.com/special/push2.git"))
  }

  @Test
  fun `test many urls are substituted for urls and push urls`(): Unit = with(context) {
    addRemote("test:group/bar.git")
    Executor.append(".git/config", """
      [remote "origin"]
        url = test:group/fetch2.git
      [url "https://github.com/"]
	      insteadOf = test:      
      [url "git@github.com:"]
	      pushInsteadOf = test:
    """.trimIndent())
    repo.update()

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("https://github.com/group/bar.git", "https://github.com/group/fetch2.git"),
                           listOf("git@github.com:group/bar.git", "git@github.com:group/fetch2.git"))
  }

  @Test
  fun `test pushUrls not set and longest placeholders are not applied to push urls even if it is not pushInsteadOf`(): Unit = with(context) {
    addRemote("test:group/bar.git")
    Executor.append(".git/config", """
      [url "https://github.com/"]
	      insteadOf = test:      
      [url "https://github.com/special/"]
	      insteadOf = test:group/      
      [url "git@github.com:"]
	      pushInsteadOf = test:
    """.trimIndent())
    repo.update()

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("https://github.com/special/bar.git"), listOf("git@github.com:group/bar.git"))
  }

  @Test
  fun `test insteadOf empty value is applied if nothing more suitable is set`(): Unit = with(context) {
    addRemote("no/prefix/group/bar.git")
    Executor.append(".git/config", """
      [url "https://github.com/"]
	      insteadOf =
      [url "https://github.com/special/"]
	      insteadOf = test:group/      
      [url "git@github.com:"]
	      pushInsteadOf = test:
    """.trimIndent())
    repo.update()

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("https://github.com/no/prefix/group/bar.git"),
                           listOf("https://github.com/no/prefix/group/bar.git"))
  }

  @Test
  fun `test insteadOf empty value is not applied if somethings more suitable is set`(): Unit = with(context) {
    addRemote("test:group/bar.git")
    Executor.append(".git/config", """
      [url "https://github.com/"]
	      insteadOf =
      [url "https://github.com/special/"]
	      insteadOf = test:group/      
      [url "git@github.com:"]
	      pushInsteadOf = test:
    """.trimIndent())
    repo.update()

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("https://github.com/special/bar.git"), listOf("git@github.com:group/bar.git"))
  }

  @Test
  fun `test pushInsteadOf empty value is applied if nothing more suitable is set`(): Unit = with(context) {
    addRemote("no/prefix/group/bar.git")
    Executor.append(".git/config", """
      [url "https://github.com/"]
	      pushInsteadOf =
      [url "https://github.com/special/"]
	      insteadOf = test:group/      
      [url "git@github.com:"]
	      pushInsteadOf = test:
    """.trimIndent())
    repo.update()

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("no/prefix/group/bar.git"), listOf("https://github.com/no/prefix/group/bar.git"))
  }

  @Test
  fun `test empty url section`(): Unit = with(context) {
    addRemote("test:group/bar.git")
    Executor.append(".git/config", """
      [url "empty"]

      [url "https://github.com/special/"]
	      insteadOf = test:group/      
      [url "git@github.com:"]
	      pushInsteadOf = test:
    """.trimIndent())
    repo.update()

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("https://github.com/special/bar.git"), listOf("git@github.com:group/bar.git"))
  }

  @Test
  fun `test duplicated url entry - both used`(): Unit = with(context) {
    addRemote("test1:group/bar.git")
    Executor.append(".git/config", """
      [remote "origin"]
        url = test2:group/fetch2.git
      [url "https://github.com/special/"]
	      insteadOf = test1:group/     
      [url "https://github.com/special/"]
	      insteadOf = test2:group/
    """.trimIndent())
    repo.update()

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("https://github.com/special/bar.git", "https://github.com/special/fetch2.git"),
                           listOf("https://github.com/special/bar.git", "https://github.com/special/fetch2.git"))
  }

  @Test
  fun `test duplicated insteadOf prefix, the first one will be used`(): Unit = with(context) {
    addRemote("test:group/bar.git")
    Executor.append(".git/config", """
      [remote "origin"]
        url = test:group/fetch2.git
      [url "https://github.com/special1/"]
	      insteadOf = test:group/     
      [url "https://github.com/special2/"]
	      insteadOf = test:group/
    """.trimIndent())
    repo.update()

    val remote = readConfig().parseRemotes().first()
    remote.checkRemoteUrls(listOf("https://github.com/special1/bar.git", "https://github.com/special1/fetch2.git"),
                           listOf("https://github.com/special1/bar.git", "https://github.com/special1/fetch2.git"))
  }

  @Test
  fun `test config values are case sensitive`(): Unit = with(context) {
    val url = "git@GITHUB.com:foo/bar.git"
    addRemote(url)

    val remote = readConfig().parseRemotes().firstOrNull()
    assertThat(remote).isNotNull()
    remote!!.checkRemoteUrls(listOf(url), listOf(url))
  }

  @Test
  fun `test config sections are case insensitive`(): Unit = with(context) {
    addRemote("git@github.com:foo/bar.git")
    val configFile = configFile().toFile()
    FileUtil.writeToFile(configFile, FileUtil.loadFile(configFile).replace("remote", "REMOTE"))

    assertSingleRemoteInConfig()
  }

  @Test
  fun `test config section values are case sensitive`(): Unit = with(context) {
    val expectedName = "ORIGIN"
    addRemote(expectedName, "git@github.com:foo/bar.git")

    val remote = readConfig().parseRemotes().firstOrNull()
    assertThat(remote).isNotNull()
    assertThat(remote!!.name).describedAs("Remote name is incorrect").isEqualTo(expectedName)
  }

  @Test
  fun `test relative hook path is extracted from config`(): Unit = with(context) {
    createHook(".githooks/pre-commit")
    repo.update()

    assertThat(repo.info.hooksInfo.areCommitHooksAvailable).isFalse()
    assertThat(repo.info.hooksInfo.isPrePushHookAvailable).isFalse()

    tac("file1.txt")

    git("config core.hooksPath .githooks/")
    repo.update()

    assertThat(repo.info.hooksInfo.areCommitHooksAvailable).isTrue()
    assertThat(repo.info.hooksInfo.isPrePushHookAvailable).isFalse()

    assertHookFailure {
      tac("file2.txt")
    }

    git("config core.hooksPath .githooks")
    repo.update()

    assertThat(repo.info.hooksInfo.areCommitHooksAvailable).isTrue()
    assertThat(repo.info.hooksInfo.isPrePushHookAvailable).isFalse()

    assertHookFailure {
      tac("file3.txt")
    }

    git("config core.hooksPath .githooks2")
    repo.update()

    assertThat(repo.info.hooksInfo.areCommitHooksAvailable).isFalse()
    assertThat(repo.info.hooksInfo.isPrePushHookAvailable).isFalse()

    tac("file4.txt")

    createHook(".githooks2/pre-push")
    repo.update()

    assertThat(repo.info.hooksInfo.areCommitHooksAvailable).isFalse()
    assertThat(repo.info.hooksInfo.isPrePushHookAvailable).isTrue()
  }

  @Test
  fun `test absolute hook path is extracted from config`(): Unit = with(context) {
    val hookFile = createHook(".githooks/pre-commit")
    repo.update()

    assertThat(repo.info.hooksInfo.areCommitHooksAvailable).isFalse()
    assertThat(repo.info.hooksInfo.isPrePushHookAvailable).isFalse()

    tac("file1.txt")

    git("config core.hooksPath " + hookFile.parent)
    repo.update()

    assertThat(repo.info.hooksInfo.areCommitHooksAvailable).isTrue()
    assertThat(repo.info.hooksInfo.isPrePushHookAvailable).isFalse()

    assertHookFailure {
      tac("file2.txt")
    }
  }

  @Test
  fun `test last hook path is extracted from config`(): Unit = with(context) {
    createHook(".githooks4/pre-commit")
    repo.update()

    assertThat(repo.info.hooksInfo.areCommitHooksAvailable).isFalse()
    assertThat(repo.info.hooksInfo.isPrePushHookAvailable).isFalse()

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

    assertThat(repo.info.hooksInfo.areCommitHooksAvailable).isTrue()
    assertThat(repo.info.hooksInfo.isPrePushHookAvailable).isFalse()

    assertHookFailure {
      tac("file2.txt")
    }
  }

  private fun GitRemote.checkRemoteUrls(expectedUrls: List<String>, expectedPushUrls: List<String>) {
    val handler = GitLineHandler(context.project, context.projectRoot, GitCommand.REMOTE)
    handler.isEnableInteractiveCallbacks = false
    handler.setSilent(true)
    handler.addParameters("-v")
    val output = Git.getInstance().runCommand(handler).output
    assertThat(output.first { it.endsWith("(fetch)") })
      .describedAs("Git remote response doesn't contain the expected value for fetch")
      .isEqualTo(expectedUrls.map { "origin\t$it (fetch)" }.first())
    assertThat(output.filter { it.endsWith("(push)") })
      .describedAs("Git remote response doesn't contain the expected value for push")
      .containsExactlyInAnyOrderElementsOf(expectedPushUrls.map { "origin\t$it (push)" })
    assertThat(urls).isEqualTo(expectedUrls)
    assertThat(pushUrls).isEqualTo(expectedPushUrls)
  }

  private fun readConfig(): GitConfig = GitConfig.read(context.project, context.projectNioRoot)

  private fun configFile(): Path = Path.of(context.projectPath, ".git", "config")

  private fun assertSingleRemoteInConfig() {
    val remotes = readConfig().parseRemotes()
    assertThat(remotes).describedAs("Number of remotes is incorrect").hasSize(1)
    val remote = remotes.first()
    assertThat(remote.name).isEqualTo("origin")
    assertThat(remote.firstUrl).isEqualTo("git@github.com:foo/bar.git")
  }

  private fun doTestRemotes(testName: String, configFile: Path, resultFile: File) {
    Files.copy(configFile, context.projectNioRoot.resolve(".git/config"), StandardCopyOption.REPLACE_EXISTING)

    val config = readConfig()
    VcsTestUtil.assertEqualCollections(testName, config.parseRemotes(), readRemoteResults(resultFile))
  }

  private fun doTestBranches(testName: String, configFile: Path, resultFile: File) {
    Files.copy(configFile, context.projectNioRoot.resolve(".git/config"), StandardCopyOption.REPLACE_EXISTING)

    val expectedInfos = readBranchResults(resultFile)
    val localBranches = expectedInfos.map { it.localBranch }
    val remoteBranches = expectedInfos.map { it.remoteBranch }

    val trackInfos = readConfig().parseTrackInfos(localBranches, remoteBranches)
    VcsTestUtil.assertEqualCollections(testName, trackInfos, expectedInfos)
  }
}

private class TestSpec(val name: String, val config: Path, val result: File)

private fun GitSingleRepoContext.addRemote(url: String) = addRemote("origin", url)

private fun GitSingleRepoContext.addRemote(name: String, url: String) {
  git("remote add $name $url")
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

private fun getTestDataFolder(subfolder: String): File = TestDataUtil.basePath.resolve("config/$subfolder").toFile()

private fun loadConfigData(dataFolder: File): Collection<TestSpec> {
  val tests = dataFolder.listFiles { _, name -> !name.startsWith(".") }
  val data = mutableListOf<TestSpec>()
  for (testDir in tests) {
    var descriptionFile: File? = null
    var configFile: File? = null
    var resultFile: File? = null
    val files = testDir.listFiles()
    assertThat(files).describedAs("No test specifications found in ${testDir.path}").isNotNull()
    for (file in files!!) {
      when {
        file.name.endsWith("_desc.txt") -> descriptionFile = file
        file.name.endsWith("_config.txt") -> configFile = file
        file.name.endsWith("_result.txt") -> resultFile = file
      }
    }
    val message = " file not found in $testDir among ${testDir.list().contentToString()}"
    assertThat(descriptionFile).describedAs("description $message").isNotNull()
    assertThat(configFile).describedAs("config $message").isNotNull()
    assertThat(resultFile).describedAs("result $message").isNotNull()

    val testName = FileUtil.loadFile(descriptionFile!!).lines()[0] // description is in the first line of the desc-file
    if (!testName.lowercase(Locale.getDefault()).startsWith("ignore")) {
      data.add(TestSpec(testName, configFile!!.toPath(), resultFile!!))
    }
  }
  return data
}

private fun readBranchResults(file: File): Collection<GitBranchTrackInfo> {
  val content = FileUtil.loadFile(file)
  val remotes = ArrayList<GitBranchTrackInfo>()
  val remStrings = StringUtil.split(content, "BRANCH")
  for (remString in remStrings) {
    if (remString.isNullOrBlank()) {
      continue
    }
    val info = StringUtil.splitByLines(remString.trim { it <= ' ' })
    val branch = info[0]
    val remote = getRemote(info[1])
    val remoteBranchAtRemote = info[2]
    // unused val remoteBranchHere = info[3]
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
    if (remString.isBlank()) {
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
