// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.data.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.Processor
import com.intellij.util.io.*
import com.intellij.util.io.storage.AbstractStorage
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsUser
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
import java.util.function.ToIntFunction

internal class PhmVcsLogStore(
  storageId: StorageId,
  storageLockContext: StorageLockContext,
  disposable: Disposable,
) : VcsLogStore {
  private val messages: PersistentHashMap<Int, String>
  private val parents: PersistentHashMap<Int, IntArray>
  private val committers: PersistentHashMap<Int, Int>
  private val timestamps: PersistentHashMap<Int, LongArray>

  private val renames: PersistentHashMap<IntArray, IntArray>

  @Volatile
  override var isFresh = false

  init {
    val commitStorage = storageId.getStorageFile("messages")
    isFresh = !Files.exists(commitStorage)

    messages = PersistentHashMap(
      /* file = */ commitStorage,
      /* keyDescriptor = */ EnumeratorIntegerDescriptor.INSTANCE,
      /* valueExternalizer = */ EnumeratorStringDescriptor.INSTANCE,
      /* initialSize = */ AbstractStorage.PAGE_SIZE, /* version = */ storageId.version,
      /* lockContext = */ storageLockContext,
    )

    val parentsStorage = storageId.getStorageFile("parents")
    parents = PersistentHashMap(
      /* file = */ parentsStorage,
      /* keyDescriptor = */ EnumeratorIntegerDescriptor.INSTANCE,
      /* valueExternalizer = */ IntListDataExternalizer(),
      /* initialSize = */ AbstractStorage.PAGE_SIZE,
      /* version = */ storageId.version,
      /* lockContext = */ storageLockContext
    )

    val committerStorage = storageId.getStorageFile("committers")
    committers = PersistentHashMap(
      /* file = */ committerStorage,
      /* keyDescriptor = */ EnumeratorIntegerDescriptor.INSTANCE,
      /* valueExternalizer = */ EnumeratorIntegerDescriptor.INSTANCE,
      /* initialSize = */ AbstractStorage.PAGE_SIZE,
      /* version = */ storageId.version,
      /* lockContext = */ storageLockContext,
    )

    val timestampsStorage = storageId.getStorageFile("timestamps")
    timestamps = PersistentHashMap(
      /* file = */ timestampsStorage,
      /* keyDescriptor = */ EnumeratorIntegerDescriptor.INSTANCE,
      /* valueExternalizer = */ LongPairDataExternalizer(),
      /* initialSize = */ AbstractStorage.PAGE_SIZE,
      /* version = */ storageId.version,
      /* lockContext = */ storageLockContext,
    )

    val storageFile = storageId.getStorageFile(VcsLogPathsIndex.RENAMES_MAP)
    renames = PersistentHashMap(/* file = */ storageFile,
                                /* keyDescriptor = */ IntPairKeyDescriptor,
                                /* valueExternalizer = */ CollectionDataExternalizer,
                                /* initialSize = */ AbstractStorage.PAGE_SIZE,
                                /* version = */ storageId.version,
                                /* lockContext = */ storageLockContext)

    Disposer.register(disposable, Disposable {
      catchAndWarn(messages::close)
      catchAndWarn(parents::close)
      catchAndWarn(committers::close)
      catchAndWarn(timestamps::close)

      catchAndWarn(renames::close)
    })
  }

  override fun createWriter(): VcsLogWriter {
    return object : VcsLogWriter {
      override fun putCommit(commitId: Int, details: VcsLogIndexer.CompressedDetails, userToId: ToIntFunction<VcsUser>) {
        messages.put(commitId, details.fullMessage)
        timestamps.put(commitId, longArrayOf(details.authorTime, details.commitTime))
        if (details.author != details.committer) {
          committers.put(commitId, userToId.applyAsInt(details.committer))
        }
      }

      override fun putParents(commitId: Int, parents: List<Hash>, hashToId: ToIntFunction<Hash>) {
        this@PhmVcsLogStore.parents.put(commitId, IntArray(parents.size) {
          hashToId.applyAsInt(parents[it])
        })
      }

      override fun flush() {
        force()
      }

      override fun close(success: Boolean) {
        force()
      }
    }
  }

  override val isEmpty: Boolean
    get() {
      return messages.keysCountApproximately() != 0
    }

  fun markCorrupted() {
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
    messages.force()
    parents.force()
    committers.force()
    timestamps.force()
  }

  override fun getMessage(commitId: Int): String? = messages.get(commitId)

  override fun getCommitter(commitId: Int): Int? = committers.get(commitId)

  override fun getTimestamp(commitId: Int): LongArray? = timestamps.get(commitId)

  override fun getParent(commitId: Int): IntArray? = parents.get(commitId)

  @Throws(IOException::class)
  override fun processMessages(processor: (Int, String) -> Boolean) {
    messages.processKeysWithExistingMapping(Processor { commit -> processor(commit, messages.get(commit)) })
  }

  override fun putRename(parent: Int, child: Int, renames: IntArray) {
    this.renames.put(intArrayOf(parent, child), renames)
  }

  override fun forceRenameMap() {
    renames.force()
  }

  override fun getRename(parent: Int, child: Int): IntArray? = renames.get(intArrayOf(parent, child))
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