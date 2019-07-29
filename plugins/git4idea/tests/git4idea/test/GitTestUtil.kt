// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("GitTestUtil")

package git4idea.test

import com.intellij.dvcs.push.PushSpec
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.HeavyPlatformTestCase
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsUser
import git4idea.GitRemoteBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitUtil
import git4idea.GitVcs
import git4idea.config.GitVersionSpecialty
import git4idea.log.GitLogProvider
import git4idea.push.GitPushSource
import git4idea.push.GitPushTarget
import git4idea.repo.GitRepository
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import java.io.File

const val USER_NAME = "John Doe"
const val USER_EMAIL = "John.Doe@example.com"

/**
 *
 * Creates file structure for given paths. Path element should be a relative (from project root)
 * path to a file or a directory. All intermediate paths will be created if needed.
 * To create a dir without creating a file pass "dir/" as a parameter.
 *
 * Usage example:
 * `createFileStructure("a.txt", "b.txt", "dir/c.txt", "dir/subdir/d.txt", "anotherdir/");`
 *
 * This will create files a.txt and b.txt in the project dir, create directories dir, dir/subdir and anotherdir,
 * and create file c.txt in dir and d.txt in dir/subdir.
 *
 * Note: use forward slash to denote directories, even if it is backslash that separates dirs in your system.
 *
 * All files are populated with "initial content" string.
 */
fun createFileStructure(rootDir: VirtualFile, vararg paths: String) {
  for (path in paths) {
    cd(rootDir)
    val dir = path.endsWith("/")
    if (dir) {
      mkdir(path)
    }
    else {
      touch(path, "initial_content_in_{$path}")
    }
  }
}

fun initRepo(project: Project, repoRoot: String, makeInitialCommit: Boolean) {
  cd(repoRoot)
  git(project, "init")
  setupDefaultUsername(project)
  if (makeInitialCommit) {
    touch("initial.txt")
    git(project, "add initial.txt")
    git(project, "commit -m initial")
  }
}

fun GitPlatformTest.cloneRepo(source: String, destination: String, bare: Boolean) {
  cd(source)
  if (bare) {
    git("clone --bare -- . $destination")
  }
  else {
    git("clone -- . $destination")
  }
  cd(destination)
  setupDefaultUsername()
}

fun setupDefaultUsername(project: Project) = setupUsername(project, USER_NAME, USER_EMAIL)
fun GitPlatformTest.setupDefaultUsername() = setupDefaultUsername(project)

fun setupUsername(project: Project, name: String, email: String) {
  assertFalse("Can not set empty user name ", name.isEmpty())
  assertFalse("Can not set empty user email ", email.isEmpty())
  git(project, "config user.name '$name'")
  git(project, "config user.email '$email'")
}

fun disableGitGc(project: Project) {
  git(project, "config gc.auto 0")
}

/**
 * Creates a Git repository in the given root directory;
 * registers it in the Settings;
 * return the [GitRepository] object for this newly created repository.
 */
fun createRepository(project: Project, root: String) = createRepository(project, root, true)

fun createRepository(project: Project, root: String, makeInitialCommit: Boolean): GitRepository {
  initRepo(project, root, makeInitialCommit)
  val gitDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(root, GitUtil.DOT_GIT))
  assertNotNull(gitDir)
  return registerRepo(project, root)
}

fun GitRepository.createSubRepository(name: String): GitRepository {
  val childRoot = File(this.root.path, name)
  HeavyPlatformTestCase.assertTrue(childRoot.mkdir())
  val repo = createRepository(this.project, childRoot.path)
  this.tac(".gitignore", name)
  return repo
}

fun registerRepo(project: Project, root: String): GitRepository {
  val vcsManager = ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl
  vcsManager.setDirectoryMapping(root, GitVcs.NAME)
  val file = LocalFileSystem.getInstance().findFileByIoFile(File(root))
  assertFalse("There are no VCS roots. Active VCSs: ${vcsManager.allActiveVcss}", vcsManager.allVcsRoots.isEmpty())
  val repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(file)
  assertNotNull("Couldn't find repository for root $root", repository)
  cd(root)
  disableGitGc(project)
  return repository!!
}

fun assumeSupportedGitVersion(vcs: GitVcs) {
  val version = vcs.version
  assumeTrue("Unsupported Git version: $version", version.isSupported)
}

fun GitPlatformTest.readAllRefs(root: VirtualFile, objectsFactory: VcsLogObjectsFactory): Set<VcsRef> {
  val refs = git("log --branches --tags --no-walk --format=%H%d --decorate=full").lines()
  val result = mutableSetOf<VcsRef>()
  for (ref in refs) {
    result.addAll(RefParser(objectsFactory).parseCommitRefs(ref, root))
  }
  return result
}

fun GitPlatformTest.makeCommit(file: String): String {
  append(file, "some content")
  addCommit("some message")
  return last()
}

fun GitPlatformTest.makeCommit(author: VcsUser, file: String): String {
  setupUsername(project, author.name, author.email)
  val commit = modify(file)
  setupDefaultUsername(project)
  return commit
}

fun findGitLogProvider(project: Project): GitLogProvider {
  val providers = VcsLogProvider.LOG_PROVIDER_EP.getExtensions(project)
    .filter { provider -> provider.supportedVcs == GitVcs.getKey() }
  assertEquals("Incorrect number of GitLogProviders", 1, providers.size)
  return providers[0] as GitLogProvider
}

fun makePushSpec(repository: GitRepository, from: String, to: String): PushSpec<GitPushSource, GitPushTarget> {
  val source = repository.branches.findLocalBranch(from)!!
  var target: GitRemoteBranch? = repository.branches.findBranchByName(to) as GitRemoteBranch?
  val newBranch: Boolean
  if (target == null) {
    val firstSlash = to.indexOf('/')
    val remote = GitUtil.findRemoteByName(repository, to.substring(0, firstSlash))!!
    target = GitStandardRemoteBranch(remote, to.substring(firstSlash + 1))
    newBranch = true
  }
  else {
    newBranch = false
  }
  return PushSpec(GitPushSource.create(source), GitPushTarget(target, newBranch))
}

fun GitRepository.resolveConflicts() {
  cd(this)
  this.git("add -u .")
}

fun getPrettyFormatTagForFullCommitMessage(project: Project) =
  if (GitVersionSpecialty.STARTED_USING_RAW_BODY_IN_FORMAT.existsIn(project)) "%B" else "%s%n%n%-b"
