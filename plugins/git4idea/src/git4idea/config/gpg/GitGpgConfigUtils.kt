// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.config.gpg

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import git4idea.commands.GitImpl
import git4idea.config.GitConfigUtil
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.util.StringScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Throws(VcsException::class)
private fun readGitGpgConfig(repository: GitRepository): RepoConfig {
  // TODO: "tag.gpgSign" ?
  val isEnabled = isGpgSignEnabled(repository.project, repository.root)
  if (!isEnabled) return RepoConfig(null)
  val keyValue = GitConfigUtil.getValue(repository.project, repository.root, GitConfigUtil.GPG_COMMIT_SIGN_KEY)
  if (keyValue == null) return RepoConfig(null)
  return RepoConfig(GpgKey(keyValue.trim()))
}

@Throws(VcsException::class)
private fun readAvailableSecretKeys(project: Project): SecretKeys {
  val repository = GitRepositoryManager.getInstance(project).repositories.firstOrNull()
  if (repository == null) return SecretKeys(emptyList(), emptyMap())

  val gpgCommand = GitConfigUtil.getValue(project, repository.root, GitConfigUtil.GPG_PROGRAM) ?: "gpg"
  val output = GitImpl.runBundledCommand(project, gpgCommand, "--list-secret-keys",
                                         "--with-colons", "--fixed-list-mode", "--batch", "--no-tty")
  return parseSecretKeys(output)
}

/**
 * See https://github.com/gpg/gnupg/blob/master/doc/DETAILS
 */
private fun parseSecretKeys(output: String): SecretKeys {
  val result = mutableListOf<GpgKey>()
  val descriptions = mutableMapOf<GpgKey, @NlsSafe String>()

  var lastKey: GpgKey? = null

  val scanner = StringScanner(output)
  while (scanner.hasMoreData()) {
    val line = scanner.line()
    val fields = line.split(':')
    val type = fields.getOrNull(0) // Field 1 - Type of record
    if (type == "sec") {
      val keyId = fields.getOrNull(4)  // Field 5 - KeyID
      val capabilities = fields.getOrNull(11) // Field 12 - Key capabilities
      if (keyId != null && capabilities != null && checkKeyCapabilities(capabilities)) {
        val gpgKey = GpgKey(keyId)
        result.add(gpgKey)
        lastKey = gpgKey
      }
      else {
        lastKey = null
      }
    }
    if (type == "uid") {
      val userId = fields.getOrNull(9) // Field 10 - User-ID
      if (userId != null && lastKey != null) {
        descriptions[lastKey] = StringUtil.unescapeAnsiStringCharacters(userId)
      }
      lastKey = null
    }
  }

  return SecretKeys(result, descriptions)
}

private fun checkKeyCapabilities(capabilities: String): Boolean {
  if (capabilities.contains("D")) return false // key Disabled
  return capabilities.contains("s") || capabilities.contains("S")  // can Sign
}

fun isGpgSignEnabled(project: Project, root: VirtualFile): Boolean {
  try {
    return GitConfigUtil.getBooleanValue(GitConfigUtil.getValue(project, root, GitConfigUtil.GPG_COMMIT_SIGN)) == true
  }
  catch (e: VcsException) {
    logger<GitConfigUtil>().warn("Cannot get gpg.commitSign config value", e)
    return false
  }
}

@Throws(VcsException::class)
fun writeGitGpgConfig(repository: GitRepository, gpgKey: GpgKey?) {
  if (gpgKey != null) {
    GitConfigUtil.setValue(repository.project, repository.root, GitConfigUtil.GPG_COMMIT_SIGN, "true")
    GitConfigUtil.setValue(repository.project, repository.root, GitConfigUtil.GPG_COMMIT_SIGN_KEY, gpgKey.id)
  }
  else {
    GitConfigUtil.setValue(repository.project, repository.root, GitConfigUtil.GPG_COMMIT_SIGN, "false")
  }
}

class RepoConfig(val key: GpgKey?)
class SecretKeys(val keys: List<GpgKey>, val descriptions: Map<GpgKey, @NlsSafe String>)
data class GpgKey(val id: @NlsSafe String)

class RepoConfigValue(val repository: GitRepository) : Value<RepoConfig>() {
  override fun compute(): RepoConfig = readGitGpgConfig(repository)
}

class SecretKeysValue(val project: Project) : Value<SecretKeys>() {
  override fun compute(): SecretKeys = readAvailableSecretKeys(project)
}

abstract class Value<T> {
  private val listeners = mutableListOf<ValueListener>()

  @Volatile
  var value: T? = null
    private set
  var error: VcsException? = null
    private set

  @Throws(VcsException::class)
  protected abstract fun compute(): T

  @Throws(VcsException::class)
  suspend fun reload(): T {
    try {
      val newValue = withContext(Dispatchers.IO) {
        coroutineToIndicator { compute() }
      }
      value = newValue
      error = null
      notifyListeners()
      return newValue
    }
    catch (e: VcsException) {
      value = null
      error = e
      notifyListeners()
      throw e
    }
  }

  suspend fun tryReload(): T? {
    try {
      return reload()
    }
    catch (e: VcsException) {
      logger<Value<*>>().warn(e)
      return null
    }
  }

  @Throws(VcsException::class)
  suspend fun load(): T {
    val value = value
    if (value != null) return value
    return reload()
  }

  suspend fun tryLoad(): T? {
    try {
      return load()
    }
    catch (e: VcsException) {
      logger<Value<*>>().warn(e)
      return null
    }
  }

  fun addListener(listener: ValueListener) {
    listeners.add(listener)
  }

  private fun notifyListeners() {
    for (listener in listeners) {
      listener()
    }
  }
}

private typealias ValueListener = () -> Unit
