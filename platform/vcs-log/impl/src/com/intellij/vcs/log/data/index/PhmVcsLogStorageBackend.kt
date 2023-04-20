// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.vcs.log.data.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Processor
import com.intellij.util.io.*
import com.intellij.util.io.storage.AbstractStorage
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogTextFilter
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsLogErrorHandler
import com.intellij.vcs.log.impl.VcsLogIndexer
import com.intellij.vcs.log.util.StorageId
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.io.DataInput
import java.io.DataOutput
import java.io.IOException
import java.nio.file.Files
import java.util.*
import java.util.function.IntConsumer
import java.util.function.IntFunction
import java.util.function.ToIntFunction

internal class PhmVcsLogStorageBackend(
  storageId: StorageId,
  storageLockContext: StorageLockContext,
  errorHandler: VcsLogErrorHandler,
  disposable: Disposable,
) : VcsLogStorageBackend {
  private val messages: PersistentHashMap<Int, String>
  private val parents: PersistentHashMap<Int, IntArray>
  private val committers: PersistentHashMap<Int, Int>
  private val timestamps: PersistentHashMap<Int, LongArray>

  private val renames: PersistentHashMap<IntArray, IntArray>

  private val trigrams: VcsLogMessagesTrigramIndex

  @Volatile
  override var isFresh = false

  init {
    val messagesStorage = storageId.getStorageFile("messages")
    isFresh = !Files.exists(messagesStorage)

    messages = PersistentHashMap(
      /* file = */ messagesStorage,
      /* keyDescriptor = */ EnumeratorIntegerDescriptor.INSTANCE,
      /* valueExternalizer = */ EnumeratorStringDescriptor.INSTANCE,
      /* initialSize = */ AbstractStorage.PAGE_SIZE, /* version = */ storageId.version,
      /* lockContext = */ storageLockContext,
    )
    Disposer.register(disposable, Disposable { catchAndWarn(messages::close) })

    val parentsStorage = storageId.getStorageFile("parents")
    parents = PersistentHashMap(
      /* file = */ parentsStorage,
      /* keyDescriptor = */ EnumeratorIntegerDescriptor.INSTANCE,
      /* valueExternalizer = */ IntListDataExternalizer(),
      /* initialSize = */ AbstractStorage.PAGE_SIZE,
      /* version = */ storageId.version,
      /* lockContext = */ storageLockContext
    )
    Disposer.register(disposable, Disposable { catchAndWarn(parents::close) })

    val committerStorage = storageId.getStorageFile("committers")
    committers = PersistentHashMap(
      /* file = */ committerStorage,
      /* keyDescriptor = */ EnumeratorIntegerDescriptor.INSTANCE,
      /* valueExternalizer = */ EnumeratorIntegerDescriptor.INSTANCE,
      /* initialSize = */ AbstractStorage.PAGE_SIZE,
      /* version = */ storageId.version,
      /* lockContext = */ storageLockContext,
    )
    Disposer.register(disposable, Disposable { catchAndWarn(committers::close) })

    val timestampsStorage = storageId.getStorageFile("timestamps")
    timestamps = PersistentHashMap(
      /* file = */ timestampsStorage,
      /* keyDescriptor = */ EnumeratorIntegerDescriptor.INSTANCE,
      /* valueExternalizer = */ LongPairDataExternalizer(),
      /* initialSize = */ AbstractStorage.PAGE_SIZE,
      /* version = */ storageId.version,
      /* lockContext = */ storageLockContext,
    )
    Disposer.register(disposable, Disposable { catchAndWarn(timestamps::close) })

    val storageFile = storageId.getStorageFile(VcsLogPathsIndex.RENAMES_MAP)
    renames = PersistentHashMap(/* file = */ storageFile,
                                /* keyDescriptor = */ IntPairKeyDescriptor,
                                /* valueExternalizer = */ CollectionDataExternalizer,
                                /* initialSize = */ AbstractStorage.PAGE_SIZE,
                                /* version = */ storageId.version,
                                /* lockContext = */ storageLockContext)
    Disposer.register(disposable, Disposable { catchAndWarn(renames::close) })

    trigrams = VcsLogMessagesTrigramIndex(storageId, storageLockContext, errorHandler, disposable)
  }

  override val trigramsEmpty: Boolean
    get() = trigrams.isEmpty

  override fun createWriter(): VcsLogWriter {
    return object : VcsLogWriter {
      override fun putCommit(commitId: Int, details: VcsLogIndexer.CompressedDetails, userToId: ToIntFunction<VcsUser>) {
        timestamps.put(commitId, longArrayOf(details.authorTime, details.commitTime))
        if (details.author != details.committer) {
          committers.put(commitId, userToId.applyAsInt(details.committer))
        }
        trigrams.update(commitId, details)
        messages.put(commitId, details.fullMessage)
      }

      override fun putParents(commitId: Int, parents: List<Hash>, hashToId: ToIntFunction<Hash>) {
        this@PhmVcsLogStorageBackend.parents.put(commitId, IntArray(parents.size) {
          hashToId.applyAsInt(parents[it])
        })
      }

      override fun flush() {
        force()
      }

      override fun close(performCommit: Boolean) {
        force()
      }

      override fun putRename(parent: Int, child: Int, renames: IntArray) {
        this@PhmVcsLogStorageBackend.putRename(parent, child, renames)
      }
    }
  }

  override val isEmpty: Boolean
    get() {
      return messages.keysCountApproximately() == 0
    }

  override fun markCorrupted() {
    messages.markCorrupted()
    messages.force()
  }

  override fun containsCommit(commitId: Int) = messages.containsMapping(commitId)

  override fun collectMissingCommits(commitIds: IntSet, missing: IntSet) {
    commitIds.forEach(IntConsumer {
      if (!messages.containsMapping(it)) {
        missing.add(it)
      }
    })
  }

  fun force() {
    parents.force()
    committers.force()
    timestamps.force()
    trigrams.flush()
    messages.force()
  }

  override fun getMessage(commitId: Int): String? = messages.get(commitId)

  override fun getCommitterOrAuthor(commitId: Int, getUserById: IntFunction<VcsUser>, getAuthorForCommit: IntFunction<VcsUser>): VcsUser? {
    val committer = committers.get(commitId)
    if (committer != null) {
      return getUserById.apply(committer)
    }
    else {
      return if (messages.containsMapping(commitId)) getAuthorForCommit.apply(commitId) else null
    }
  }

  override fun getTimestamp(commitId: Int): LongArray? = timestamps.get(commitId)

  override fun getParent(commitId: Int): IntArray? = parents.get(commitId)

  @Throws(IOException::class)
  override fun processMessages(processor: (Int, String) -> Boolean) {
    messages.processKeysWithExistingMapping(Processor { commit -> processor(commit, messages.get(commit) ?: return@Processor true) })
  }

  override fun putRename(parent: Int, child: Int, renames: IntArray) {
    this.renames.put(intArrayOf(parent, child), renames)
  }

  override fun forceRenameMap() {
    renames.force()
  }

  override fun getRename(parent: Int, child: Int): IntArray? = renames.get(intArrayOf(parent, child))

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
        if (filter.matches(message)) {
          consumer.accept(commit)
        }
      }
    }
  }
}

private inline fun catchAndWarn(runnable: () -> Unit) {
  try {
    runnable()
  }
  catch (e: IOException) {
    VcsLogPersistentIndex.LOG.warn(e)
  }
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

internal class MyCommitIdKeyDescriptor(private val roots: List<VirtualFile>) : KeyDescriptor<CommitId> {
  private val rootsReversed = Object2IntOpenHashMap<VirtualFile>(roots.size)

  init {
    for (i in roots.indices) {
      rootsReversed.put(roots.get(i), i)
    }
  }

  override fun save(out: DataOutput, value: CommitId) {
    (value.hash as HashImpl).write(out)
    out.writeInt(rootsReversed.getInt(value.root))
  }

  override fun read(`in`: DataInput): CommitId {
    val hash = HashImpl.read(`in`)
    val root = roots.get(`in`.readInt())
    return CommitId(hash, root)
  }

  override fun getHashCode(value: CommitId): Int {
    var result = value.hash.hashCode()
    result = 31 * result + rootsReversed.getInt(value)
    return result
  }

  override fun isEqual(val1: CommitId?, val2: CommitId?): Boolean {
    if (val1 === val2) return true
    return if (val1 == null || val2 == null) {
      false
    }
    else {
      val1.hash == val2.hash && rootsReversed.getInt(val1.root) == rootsReversed.getInt(val2.root)
    }
  }
}