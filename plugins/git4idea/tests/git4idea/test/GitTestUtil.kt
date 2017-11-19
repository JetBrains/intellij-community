/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
@file:JvmName("GitTestUtil")

package git4idea.test

import com.intellij.dvcs.push.PushSpec
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.Executor.*
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogObjectsFactory
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsRef
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
      touch(path, "initial_content_" + Math.random())
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

fun registerRepo(project: Project, root: String): GitRepository {
  val vcsManager = ProjectLevelVcsManager.getInstance(project) as ProjectLevelVcsManagerImpl
  vcsManager.setDirectoryMapping(root, GitVcs.NAME)
  val file = LocalFileSystem.getInstance().findFileByIoFile(File(root))
  assertFalse(vcsManager.allVcsRoots.isEmpty())
  val repository = GitUtil.getRepositoryManager(project).getRepositoryForRoot(file)
  assertNotNull("Couldn't find repository for root " + root, repository)
  return repository!!
}

fun assumeSupportedGitVersion(vcs: GitVcs) {
  val version = vcs.version
  assumeTrue("Unsupported Git version: " + version, version.isSupported)
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

fun findGitLogProvider(project: Project): GitLogProvider {
  val providers = Extensions.getExtensions(VcsLogProvider.LOG_PROVIDER_EP, project)
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
  if (GitVersionSpecialty.STARTED_USING_RAW_BODY_IN_FORMAT.existsIn(GitVcs.getInstance(project).version)) "%B" else "%s%n%n%-b"
