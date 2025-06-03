// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.vcs.log.data.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Processor
import com.intellij.util.indexing.StorageException
import com.intellij.util.io.*
import com.intellij.util.io.storage.AbstractStorage
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.VcsLogStorage
import com.intellij.vcs.log.data.VcsLogStorageImpl
import com.intellij.vcs.log.history.EdgeData
import com.intellij.vcs.log.impl.VcsLogErrorHandler
import com.intellij.vcs.log.impl.VcsLogIndexer
import com.intellij.vcs.log.impl.VcsLogIndexer.PathsEncoder
import com.intellij.vcs.log.util.StorageId
import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import kotlinx.coroutines.CancellationException
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.nio.file.Files
import java.util.function.IntConsumer
import java.util.function.IntFunction
import java.util.function.ObjIntConsumer

internal class PhmVcsLogStorageBackend(
  override val storageId: StorageId.Directory,
  private val storage: VcsLogStorage,
  roots: Set<VirtualFile>,
  userRegistry: VcsUserRegistry,
  private val errorHandler: VcsLogErrorHandler,
  useDurableEnumerator: Boolean,
  disposable: Disposable,
) : VcsLogStorageBackend, Disposable {
  private class StorageContainer(
    val messages: PersistentHashMap<VcsLogCommitStorageIndex, String>,
    val parents: PersistentHashMap<VcsLogCommitStorageIndex, IntArray>,
    val committers: PersistentHashMap<VcsLogCommitStorageIndex, Int>,
    val timestamps: PersistentHashMap<VcsLogCommitStorageIndex, LongArray>,

    val renames: PersistentHashMap<IntArray, IntArray>,

    val trigrams: VcsLogMessagesTrigramIndex,
    val paths: VcsLogPathsIndex,
    val users: VcsLogUserIndex,
  )

  // we have to nullize references on disposal to ensure that backing files can be closed and deleted
  private var _container: StorageContainer?
  private val container: StorageContainer
    get() = _container ?: error("Storage is closed: ${storageId}")

  private val messages get() = container.messages
  private val parents get() = container.parents
  private val committers get() = container.committers
  private val timestamps get() = container.timestamps

  private val renames get() = container.renames

  private val trigrams get() = container.trigrams
  private val paths get() = container.paths
  private val users get() = container.users

  @Volatile
  override var isFresh = false
  override val isEmpty: Boolean get() = messages.keysCountApproximately() == 0

  init {
    Disposer.register(disposable, this)

    try {
      val storageLockContext = StorageLockContext()

      val messagesStorage = storageId.getStorageFile("messages")
      isFresh = !Files.exists(messagesStorage)

      val messages = PersistentHashMap(
        /* file = */ messagesStorage,
        /* keyDescriptor = */ EnumeratorIntegerDescriptor.INSTANCE,
        /* valueExternalizer = */ EnumeratorStringDescriptor.INSTANCE,
        /* initialSize = */ AbstractStorage.PAGE_SIZE, /* version = */ storageId.version,
        /* lockContext = */ storageLockContext,
      )
      Disposer.register(this, Disposable { catchAndWarn(messages::close) })

      val parentsStorage = storageId.getStorageFile("parents")
      val parents = PersistentHashMap(
        /* file = */ parentsStorage,
        /* keyDescriptor = */ EnumeratorIntegerDescriptor.INSTANCE,
        /* valueExternalizer = */ IntListDataExternalizer(),
        /* initialSize = */ AbstractStorage.PAGE_SIZE,
        /* version = */ storageId.version,
        /* lockContext = */ storageLockContext
      )
      Disposer.register(this, Disposable { catchAndWarn(parents::close) })

      val committerStorage = storageId.getStorageFile("committers")
      val committers = PersistentHashMap(
        /* file = */ committerStorage,
        /* keyDescriptor = */ EnumeratorIntegerDescriptor.INSTANCE,
        /* valueExternalizer = */ EnumeratorIntegerDescriptor.INSTANCE,
        /* initialSize = */ AbstractStorage.PAGE_SIZE,
        /* version = */ storageId.version,
        /* lockContext = */ storageLockContext,
      )
      Disposer.register(this, Disposable { catchAndWarn(committers::close) })

      val timestampsStorage = storageId.getStorageFile("timestamps")
      val timestamps = PersistentHashMap(
        /* file = */ timestampsStorage,
        /* keyDescriptor = */ EnumeratorIntegerDescriptor.INSTANCE,
        /* valueExternalizer = */ LongPairDataExternalizer(),
        /* initialSize = */ AbstractStorage.PAGE_SIZE,
        /* version = */ storageId.version,
        /* lockContext = */ storageLockContext,
      )
      Disposer.register(this, Disposable { catchAndWarn(timestamps::close) })

      val storageFile = storageId.getStorageFile(VcsLogPathsIndex.RENAMES_MAP)
      val renames = PersistentHashMap(/* file = */ storageFile,
                                  /* keyDescriptor = */ IntPairKeyDescriptor,
                                  /* valueExternalizer = */ CollectionDataExternalizer,
                                  /* initialSize = */ AbstractStorage.PAGE_SIZE,
                                  /* version = */ storageId.version,
                                  /* lockContext = */ storageLockContext)
      Disposer.register(this, Disposable { catchAndWarn(renames::close) })

      val paths = VcsLogPathsIndex.create(storageId, storageLockContext, storage, roots, renames, useDurableEnumerator, errorHandler, this)
      val users = VcsLogUserIndex.create(storageId, storageLockContext, userRegistry, errorHandler, this)
      val trigrams = VcsLogMessagesTrigramIndex(storageId, storageLockContext, errorHandler, this)

      _container = StorageContainer(
        messages,
        parents,
        committers,
        timestamps,
        renames,
        trigrams,
        paths,
        users,
      )
      reportEmpty()
    }
    catch (t: Throwable) {
      Disposer.dispose(this)
      throw t
    }
  }

  @Throws(IOException::class)
  private fun reportEmpty() {
    if (messages.keysCountApproximately() == 0) return

    val trigramsEmpty = trigrams.isEmpty
    val usersEmpty = users.isEmpty
    val pathsEmpty = paths.isEmpty
    if (trigramsEmpty || usersEmpty || pathsEmpty) {
      VcsLogPersistentIndex.LOG.warn("Some of the index maps empty:\n" +
                                     "trigrams empty $trigramsEmpty\n" +
                                     "users empty $usersEmpty\n" +
                                     "paths empty $pathsEmpty")
    }
  }

  override fun createWriter(): VcsLogWriter {
    return object : VcsLogWriter {
      override fun putCommit(commitId: VcsLogCommitStorageIndex, details: VcsLogIndexer.CompressedDetails) {
        users.update(commitId, details)
        paths.update(commitId, details)
        trigrams.update(commitId, details)

        parents.put(commitId, IntArray(details.parents.size) {
          storage.getCommitIndex(details.parents[it], details.root)
        })
        timestamps.put(commitId, longArrayOf(details.authorTime, details.commitTime))
        if (details.author != details.committer) {
          committers.put(commitId, users.getUserId(details.committer))
        }
        messages.put(commitId, details.fullMessage)
      }

      private fun force() {
        try {
          parents.force()
          committers.force()
          timestamps.force()
          trigrams.flush()
          users.flush()
          paths.flush()
          messages.force()
        }
        catch (e: IOException) {
          errorHandler.handleError(VcsLogErrorHandler.Source.Index, e)
        }
        catch (s: StorageException) {
          errorHandler.handleError(VcsLogErrorHandler.Source.Index, s)
        }
      }

      override fun flush() = force()
      override fun close(performCommit: Boolean) = force()
      override fun interrupt() = force()
    }
  }

  override fun markCorrupted() {
    try {
      messages.markCorrupted()
      messages.force()
    }
    catch (t: Throwable) {
      LOG.warn(t)
    }
  }

  override fun containsCommit(commitId: VcsLogCommitStorageIndex) = messages.containsMapping(commitId)

  override fun collectMissingCommits(commitIds: IntSet): IntSet {
    val missing = IntOpenHashSet()
    commitIds.forEach(IntConsumer {
      if (!messages.containsMapping(it)) {
        missing.add(it)
      }
    })
    return missing
  }

  @Throws(IOException::class)
  override fun iterateIndexedCommits(limit: Int, processor: IntFunction<Boolean>) {
    var iterationCount = 0
    messages.processKeysWithExistingMapping {
      if (iterationCount >= limit) return@processKeysWithExistingMapping false
      iterationCount++
      processor.apply(it)
    }
  }

  override fun getTimestamp(commitId: VcsLogCommitStorageIndex): LongArray? = timestamps.get(commitId)

  override fun getParents(commitId: VcsLogCommitStorageIndex): IntArray? = parents.get(commitId)

  override fun getParents(commitIds: Collection<VcsLogCommitStorageIndex>): Map<VcsLogCommitStorageIndex, List<Hash>> {
    return commitIds.mapNotNull { commitId ->
      val parents = getParents(commitId) ?: return@mapNotNull null
      val parentHashes = storage.getHashes(parents) ?: return@mapNotNull null
      commitId to parentHashes
    }.toMap()
  }

  override fun getMessage(commitId: VcsLogCommitStorageIndex): String? = messages.get(commitId)

  @Throws(IOException::class)
  override fun processMessages(processor: (Int, String) -> Boolean) {
    messages.processKeysWithExistingMapping(Processor { commit -> processor(commit, messages.get(commit) ?: return@Processor true) })
  }

  override fun getCommitsForSubstring(string: String,
                                      candidates: IntSet?,
                                      noTrigramSources: MutableList<String>,
                                      consumer: IntConsumer,
                                      filter: VcsLogTextFilter) {
    val commits = trigrams.getCommitsForSubstring(string)
    if (commits == null) {
      noTrigramSources.add(string)
      return
    }

    val iterator = commits.iterator()
    while (iterator.hasNext()) {
      val commit = iterator.nextInt()
      if (candidates == null || candidates.contains(commit)) {
        val message = messages.get(commit)
        if (message != null && filter.matches(message)) {
          consumer.accept(commit)
        }
      }
    }
  }

  override fun getAuthorForCommit(commitId: VcsLogCommitStorageIndex): VcsUser? {
    return users.getAuthorForCommit(commitId)
  }

  override fun getCommitterForCommit(commitId: VcsLogCommitStorageIndex): VcsUser? {
    val committer = committers.get(commitId)
    if (committer != null) {
      return users.getUserById(committer)
    }
    else {
      return if (messages.containsMapping(commitId)) getAuthorForCommit(commitId) else null
    }
  }

  override fun getCommitsForUsers(users: Set<VcsUser>): IntSet {
    return this.users.getCommitsForUsers(users)
  }

  @Throws(IOException::class)
  override fun findRename(parent: VcsLogCommitStorageIndex, child: VcsLogCommitStorageIndex, root: VirtualFile, path: FilePath, isChildPath: Boolean): EdgeData<FilePath?>? {
    val renames = renames.get(intArrayOf(parent, child))
    if (renames == null || renames.isEmpty()) {
      return null
    }
    val pathId: Int = paths.getPathId(LightFilePath(root, path))
    var i = 0
    while (i < renames.size) {
      val first = renames[i]
      val second = renames[i + 1]
      if (isChildPath && second == pathId || !isChildPath && first == pathId) {
        val path1 = paths.getPath(first, path.isDirectory)
        val path2 = paths.getPath(second, path.isDirectory)
        return EdgeData(path1, path2)
      }
      i += 2
    }
    return null
  }

  @Throws(IOException::class, StorageException::class)
  override fun iterateChangesInCommits(root: VirtualFile, path: FilePath,
                                       consumer: ObjIntConsumer<List<ChangeKind>>) {
    val pathId: Int = paths.getPathId(LightFilePath(root, path))
    paths.iterateCommitIdsAndValues(pathId, consumer)
  }

  override fun getPathsEncoder(): PathsEncoder {
    return PathsEncoder { root, relativePath, _ ->
      try {
        paths.getPathId(LightFilePath(root, relativePath))
      }
      catch (e: IOException) {
        errorHandler.handleError(VcsLogErrorHandler.Source.Index, e)
        return@PathsEncoder 0
      }
    }
  }

  private inline fun catchAndWarn(runnable: () -> Unit) {
    try {
      runnable()
    }
    catch (_: CancellationException) {
    }
    catch (t: Throwable) {
      LOG.warn(t)
    }
  }

  internal fun clearCaches() {
    for (index in listOf(trigrams, paths, users)) {
      index.clearCaches()
    }
  }

  override fun dispose() {
    _container = null
  }

  companion object {
    private val LOG = Logger.getInstance(PhmVcsLogStorageBackend::class.java)

    @NonNls
    private const val INDEX = "index"
    private const val VERSION = 3

    internal val durableEnumeratorRegistryProperty get() = Registry.get("vcs.log.index.durable.enumerator")

    @Throws(IOException::class)
    @JvmStatic
    fun create(project: Project, storage: VcsLogStorage, indexStorageId: StorageId.Directory, roots: Set<VirtualFile>,
               errorHandler: VcsLogErrorHandler, parent: Disposable): PhmVcsLogStorageBackend {
      val userRegistry = project.getService(VcsUserRegistry::class.java)
      val useDurableEnumerator = durableEnumeratorRegistryProperty.asBoolean()
      return IOUtil.openCleanOrResetBroken({
                                             PhmVcsLogStorageBackend(indexStorageId, storage, roots, userRegistry, errorHandler,
                                                                     useDurableEnumerator, parent)
                                           }) {
        if (!indexStorageId.cleanupAllStorageFiles()) {
          LOG.error("Could not clean up storage files in " + indexStorageId.storagePath)
        }
      }
    }

    @JvmStatic
    fun getIndexStorageId(project: Project, logId: String): StorageId.Directory {
      return StorageId.Directory(project.name, INDEX, logId, VcsLogStorageImpl.VERSION + VcsLogPersistentIndex.VERSION + VERSION)
    }
  }
}

internal fun VcsLogStorage.getHashes(commits: IntArray): List<Hash>? {
  val commitIds = getCommitIds(commits.asList())
  val result = ArrayList<Hash>(commits.size)
  for (parentIndex in commits) {
    val id = commitIds[parentIndex] ?: return null
    result.add(id.hash)
  }
  return result
}

private object IntPairKeyDescriptor : KeyDescriptor<IntArray> {
  override fun getHashCode(value: IntArray): Int = value.contentHashCode()

  override fun isEqual(val1: IntArray?, val2: IntArray?) = val1.contentEquals(val2)

  @Throws(IOException::class)
  override fun save(out: DataOutput, value: IntArray) {
    out.writeInt(value[0])
    out.writeInt(value[1])
  }

  override fun read(`in`: DataInput): IntArray = intArrayOf(`in`.readInt(), `in`.readInt())
}

private object CollectionDataExternalizer : DataExternalizer<IntArray> {
  override fun save(out: DataOutput, value: IntArray) {
    out.writeInt(value.size)
    for (v in value) {
      out.writeInt(v)
    }
  }

  override fun read(`in`: DataInput): IntArray {
    val size = `in`.readInt()
    val result = IntArray(size)
    for (i in 0 until size) {
      result[i] = `in`.readInt()
    }
    return result
  }
}
