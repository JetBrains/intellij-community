// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.visible

import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.*
import com.intellij.vcs.log.data.DataPack
import com.intellij.vcs.log.data.DataPackBase
import com.intellij.vcs.log.graph.VisibleGraph
import com.intellij.vcs.log.visible.filters.VcsLogFilterObject
import org.jetbrains.annotations.NonNls
import java.util.concurrent.ConcurrentHashMap

open class VisiblePack @JvmOverloads constructor(
  val dataPack: DataPackBase,
  val visibleGraph: VisibleGraph<VcsLogCommitStorageIndex>,
  val canRequestMore: Boolean,
  private val filters: VcsLogFilterCollection,
  data: Map<Key<*>, Any?> = emptyMap(),
) : VcsLogDataPack, UserDataHolder {
  val additionalData: MutableMap<Key<*>, Any?> = ConcurrentHashMap<Key<*>, Any?>(data)

  val isFull: Boolean
    get() = dataPack.isFull

  override fun getLogProviders(): Map<VirtualFile, VcsLogProvider> {
    return dataPack.logProviders
  }

  override fun getRefs(): VcsLogRefs {
    return dataPack.refsModel
  }

  override fun getFilters(): VcsLogFilterCollection {
    return filters
  }

  override fun isEmpty(): Boolean {
    return visibleGraph.visibleCommitCount == 0
  }

  open fun getRootAtHead(headCommitIndex: VcsLogCommitStorageIndex): VirtualFile? {
    return dataPack.refsModel.rootAtHead(headCommitIndex)
  }

  override fun <T> getUserData(key: Key<T>): T? {
    return additionalData[key] as T?
  }

  override fun <T> putUserData(key: Key<T>, value: T?) {
    additionalData.put(key, value)
  }

  override fun toString(): @NonNls String {
    return "VisiblePack{size=" +
           visibleGraph.visibleCommitCount +
           ", filters=" +
           filters +
           ", canRequestMore=" +
           canRequestMore + "}"
  }

  class ErrorVisiblePack(dataPack: DataPackBase, filters: VcsLogFilterCollection, val error: Throwable)
    : VisiblePack(dataPack, EmptyVisibleGraph.getInstance(), false, filters)

  companion object {
    @JvmField
    val EMPTY: VisiblePack = object : VisiblePack(DataPack.EMPTY, EmptyVisibleGraph.getInstance(), false,
                                                  VcsLogFilterObject.EMPTY_COLLECTION) {
      override fun toString(): String {
        return "EmptyVisiblePack"
      }
    }

    @JvmField
    val NO_GRAPH_INFORMATION: Key<Boolean> = Key.create<Boolean>("NO_GRAPH_INFORMATION")
  }
}
