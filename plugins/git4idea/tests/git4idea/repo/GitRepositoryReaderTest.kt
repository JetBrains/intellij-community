// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.NioFiles
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.io.ZipUtil
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import git4idea.GitReference
import git4idea.test.GitPlatformTestContext
import git4idea.test.TestDataUtil
import git4idea.test.gitPlatformContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

@TestApplication
class GitRepositoryReaderTest {

  companion object {
    @JvmStatic
    fun data(): List<Arguments> {
      Files.newDirectoryStream(TestDataUtil.basePath.resolve("repo")).use { testCases ->
        return testCases.filter(Files::isDirectory).map { Arguments.of(it.fileName.toString(), it) }
      }
    }
  }

  private val fixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = fixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  @ParameterizedTest(name = "{0}")
  @MethodSource("data")
  @RegistryKey(key = "git.read.branches.from.disk", value = "true")
  fun `test branches`(@Suppress("UNUSED_PARAMETER") name: String, testCaseDir: Path): Unit = with(context) {
    assumeTrue(Registry.`is`("git.read.branches.from.disk")) // The test data does not contain .git/objects.

    val tempDir = prepareTest(testCaseDir)
    val gitDir = tempDir.resolve(".git")
    val rootDir = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(tempDir))
    val virtualGitDir = checkNotNull(LocalFileSystem.getInstance().refreshAndFindFileByNioFile(gitDir))
    val repositoryReader = GitRepositoryReader(project, GitRepositoryFiles.createInstance(rootDir, virtualGitDir))

    val remotes = GitConfig.read(project, projectNioRoot).parseRemotes()
    val state = repositoryReader.readState(remotes)

    assertThat(state.currentRevision).describedAs("HEAD revision is incorrect").isEqualTo(readHead(tempDir))
    val currentBranch = readCurrentBranch(tempDir)
    assertThat(state.currentBranch?.name).isEqualTo(currentBranch.name)
    assertThat(state.localBranches[state.currentBranch]).isEqualTo(currentBranch.hash)
    assertReferences(state.localBranches, readRefs(tempDir, RefType.LOCAL_BRANCH))
    assertReferences(state.remoteBranches, readRefs(tempDir, RefType.REMOTE_BRANCH))
  }

  private fun GitPlatformTestContext.prepareTest(testDir: Path): Path {
    val tempPath = projectNioRoot.resolve("test")
    Files.createDirectory(tempPath)
    Disposer.register(disposable) { NioFiles.deleteRecursively(tempPath) }
    NioFiles.copyRecursively(testDir, tempPath)
    val gitDir = tempPath.resolve(".git")
    val dotGit = tempPath.resolve("dot_git")
    if (Files.notExists(dotGit)) {
      val dotGitZip = tempPath.resolve("dot_git.zip")
      assertThat(dotGitZip).describedAs("Neither dot_git nor dot_git.zip was found").exists()
      ZipUtil.extract(dotGitZip, tempPath, null)
    }
    Files.move(dotGit, gitDir, StandardCopyOption.ATOMIC_MOVE)
    assertThat(gitDir).exists()
    return tempPath
  }

  private fun readHead(dir: Path): String = Files.readString(dir.resolve("head.txt")).trim()

  private fun readCurrentBranch(resultDir: Path): Branch =
    readBranchFromLine(Files.readString(resultDir.resolve("current-branch.txt")).trim())

  private fun readBranchFromLine(branch: String): Branch {
    val branchAndHash = StringUtil.split(branch, " ")
    return Branch(branchAndHash[1], HashImpl.build(branchAndHash[0]))
  }

  private fun assertReferences(actual: Map<out GitReference, Hash>, expected: Collection<Branch>) {
    val actualBranches = actual.map { (reference, hash) -> Branch(reference.fullName, hash) }
    assertThat(actualBranches).containsExactlyInAnyOrderElementsOf(expected)
  }

  private fun readRefs(resultDir: Path, refType: RefType): Collection<Branch> =
    StringUtil.splitByLines(Files.readString(resultDir.resolve(refType.path))).map(::readBranchFromLine)

  private data class Branch(val name: String, val hash: Hash) {
    override fun toString(): String = name
  }

  private enum class RefType(val path: String) {
    LOCAL_BRANCH("local-branches.txt"),
    REMOTE_BRANCH("remote-branches.txt"),
  }
}
