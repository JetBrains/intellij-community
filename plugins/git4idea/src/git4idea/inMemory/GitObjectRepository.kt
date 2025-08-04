// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.inMemory

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import git4idea.checkin.GitCheckinEnvironment
import git4idea.commands.Git
import git4idea.commands.GitBinaryHandler
import git4idea.commands.GitCommand
import git4idea.commands.GitHandlerInputProcessorUtil
import git4idea.commands.GitLineHandler
import git4idea.commands.GitObjectType
import git4idea.config.gpg.isGpgSignEnabledCached
import git4idea.inMemory.objects.GitObject
import git4idea.inMemory.objects.Oid
import git4idea.repo.GitRepository
import java.io.IOException

/**
 * In-memory representation of a git repository.
 * Objects are cached and created in memory and persisted to disk on request
 *
 * Lifetime is a single in-memory operation
 */
internal class GitObjectRepository(val repository: GitRepository) {
  companion object {
    private val LOG = logger<GitObjectRepository>()
  }

  private val objectCache: MutableMap<Oid, GitObject> = HashMap()

  fun findObjectFromCache(oid: Oid): GitObject? {
    return objectCache[oid]
  }

  fun cacheObject(obj: GitObject) {
    objectCache.putIfAbsent(obj.oid, obj)
  }

  fun clearCache() {
    objectCache.clear()
  }

  fun findCommit(oid: Oid): GitObject.Commit {
    val obj = findObjectFromCache(oid) ?: loadObjectFromDisk(oid, GitObjectType.COMMIT)
    require(obj is GitObject.Commit) { "Object $oid is not a commit" }
    return obj
  }

  fun findTree(oid: Oid): GitObject.Tree {
    val obj = findObjectFromCache(oid) ?: loadObjectFromDisk(oid, GitObjectType.TREE)
    require(obj is GitObject.Tree) { "Object $oid is not a tree" }
    return obj
  }

  fun findBlob(oid: Oid): GitObject.Blob {
    val obj = findObjectFromCache(oid) ?: loadObjectFromDisk(oid, GitObjectType.BLOB)
    require(obj is GitObject.Blob) { "Object $oid is not a blob" }
    return obj
  }

  fun createCommit(
    body: ByteArray,
    oid: Oid,
    author: GitObject.Commit.Author,
    committer: GitObject.Commit.Author,
    parentsOids: List<Oid>,
    treeOid: Oid,
    message: ByteArray,
    gpgSignature: ByteArray?,
  ): GitObject.Commit {
    return GitObject.Commit(body, oid, author, committer, parentsOids, treeOid, message, gpgSignature).also {
      cacheObject(it)
    }
  }

  fun createTree(body: ByteArray, oid: Oid, entries: Map<GitObject.Tree.FileName, GitObject.Tree.Entry>): GitObject.Tree {
    return GitObject.Tree(body, oid, entries).also {
      cacheObject(it)
    }
  }

  fun createTree(entries: Map<GitObject.Tree.FileName, GitObject.Tree.Entry>): GitObject.Tree {
    val body = GitObject.Tree.buildBody(entries)
    val oid = fetchOid(GitObjectType.TREE, body)
    return GitObject.Tree(body, oid, entries).also {
      cacheObject(it)
    }
  }

  fun createBlob(content: ByteArray, oid: Oid): GitObject.Blob {
    return GitObject.Blob(content, oid).also {
      cacheObject(it)
    }
  }

  fun createBlob(content: ByteArray): GitObject.Blob {
    val oid = fetchOid(GitObjectType.BLOB, content)
    return GitObject.Blob(content, oid).also {
      cacheObject(it)
    }
  }

  /*
  All dependencies should be persisted on disk
   */
  @RequiresBackgroundThread
  fun commitTree(
    treeOid: Oid,
    parentsOids: List<Oid>,
    message: ByteArray,
    author: GitObject.Commit.Author,
  ): Oid {
    LOG.debug("Starting commitTree operation: treeOid=$treeOid, parents=${parentsOids}")

    val messageFile = try {
      GitCheckinEnvironment.createCommitMessageFile(repository.project, repository.root, message.toString(Charsets.UTF_8))
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: IOException) {
      LOG.warn("Couldn't create message file", e)
      throw e
    }

    val handler = GitLineHandler(repository.project, repository.root, GitCommand.COMMIT_TREE).apply {
      setSilent(true)
      parentsOids.forEach { addParameters("-p", it.hex()) }
      addCustomEnvironmentVariable("GIT_AUTHOR_NAME", author.name)
      addCustomEnvironmentVariable("GIT_AUTHOR_EMAIL", author.email)
      addCustomEnvironmentVariable("GIT_AUTHOR_DATE", author.timestamp)
      if (isGpgSignEnabledCached(repository)) {
        addParameters("--gpg-sign")
      }
      addParameters("-F")
      addAbsoluteFile(messageFile)
      addParameters(treeOid.hex())

      if (message.isEmpty()) { // in this case git will ignore -F and read message from stdin
        setInputProcessor(GitHandlerInputProcessorUtil.redirectStream(byteArrayOf().inputStream()))
      }
    }

    val oid = Oid.fromHex(Git.getInstance().runCommand(handler).getOutputOrThrow())
    LOG.debug("Successfully created commit: $oid")
    return oid
  }

  fun commitTreeWithOverrides(
    commit: GitObject.Commit,
    treeOid: Oid? = null,
    parentsOids: List<Oid>? = null,
    message: ByteArray? = null,
    author: GitObject.Commit.Author? = null,
  ): Oid {
    val newTree = treeOid ?: commit.treeOid
    val newParents = parentsOids ?: commit.parentsOids
    val newMessage = message ?: commit.message
    val newAuthor = author ?: commit.author
    return commitTree(newTree, newParents, newMessage, newAuthor)
  }

  @RequiresBackgroundThread
  fun fetchOid(type: GitObjectType, body: ByteArray): Oid {
    val handler = GitLineHandler(repository.project, repository.root, GitCommand.HASH_OBJECT).apply {
      setSilent(true)
      addParameters("-t", type.tag)
      addParameters("--stdin")
      setInputProcessor(GitHandlerInputProcessorUtil.redirectStream(body.inputStream()))
    }
    val hash = Git.getInstance().runCommand(handler).getOutputOrThrow()

    return Oid.fromHex(hash)
  }

  @RequiresBackgroundThread
  fun persistObject(obj: GitObject) {
    if (obj.persisted) return

    obj.dependencies.forEach { oid ->
      findObjectFromCache(oid)?.let {
        persistObject(it)
      }
    }

    try {
      val handler = GitLineHandler(repository.project, repository.root, GitCommand.HASH_OBJECT).apply {
        setSilent(true)
        addParameters("-t", obj.type.tag, "-w", "--stdin")
        setInputProcessor(GitHandlerInputProcessorUtil.redirectStream(obj.body.inputStream()))
      }

      val newOid = Oid.fromHex(Git.getInstance().runCommand(handler).getOutputOrThrow())
      check(newOid == obj.oid) { "Computed by git OID $newOid does not match expected OID ${obj.oid}" }

      obj.persisted = true
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error("Failed to persist object ${obj.oid}", e)
      throw e
    }
  }

  /*
  Object should be persisted on disk
   */
  @RequiresBackgroundThread
  private fun fetchObjectType(oid: Oid): GitObjectType {
    val type = Git.getInstance().getObjectTypeEnum(repository, oid.hex())
    require(type != null) { "Unknown git object type" }
    return type
  }

  @RequiresBackgroundThread
  private fun loadObjectFromDisk(oid: Oid, type: GitObjectType? = null): GitObject {
    try {
      val type = type ?: fetchObjectType(oid)

      val bodyHandler = GitBinaryHandler(repository.project, repository.root, GitCommand.CAT_FILE).apply {
        setSilent(true)
        addParameters(type.tag, oid.hex())
      }

      val body = bodyHandler.run()

      val obj = when (type) {
        GitObjectType.COMMIT -> {
          LOG.debug("Parsing commit object: $oid")
          val parsedData = GitObject.Commit.parseBody(body)
          createCommit(body,
                       oid,
                       parsedData.author,
                       parsedData.committer,
                       parsedData.parentsOids,
                       parsedData.treeOid,
                       parsedData.message,
                       parsedData.gpgSignature)
        }
        GitObjectType.TREE -> {
          LOG.debug("Parsing tree object: $oid")
          val entries = GitObject.Tree.parseBody(body)
          createTree(body, oid, entries)
        }
        GitObjectType.BLOB -> {
          createBlob(body, oid)
        }
      }

      obj.persisted = true
      LOG.debug("Successfully loaded object: $oid (${obj.type})")
      return obj
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Exception) {
      LOG.error("Failed to load object $oid from git", e)
      throw e
    }
  }
}