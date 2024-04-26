/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil

abstract class VcsStatusMerger<S, Path> {
  fun merge(statuses: List<List<S>>): List<MergedStatusInfo<S>> {
    statuses.singleOrNull()?.let { s -> return s.map { MergedStatusInfo(it) } }

    val pathsToStatusesMap = statuses.map { infos ->
      infos.mapNotNull { info -> getPath(info)?.let { Pair(it, info) } }.toMap(linkedMapOf())
    }

    val result = mutableListOf<MergedStatusInfo<S>>()

    outer@ for (path in pathsToStatusesMap.first().keys) {
      val statusesList = mutableListOf<S>()
      for (pathsToStatusesForParent in pathsToStatusesMap) {
        val status = pathsToStatusesForParent[path] ?: continue@outer
        statusesList.add(status)
      }

      result.add(MergedStatusInfo(merge(path, statusesList), statusesList))
    }

    return result
  }

  fun merge(path: Path, statuses: List<S>): S {
    val types = statuses.map { getType(it) }.distinct()

    if (types.size == 1) {
      if (types.single() == Change.Type.MOVED) {
        var renamedFrom: Path? = null
        for (status in statuses) {
          if (renamedFrom == null) {
            renamedFrom = getFirstPath(status)
          }
          else if (renamedFrom != getFirstPath(status)) {
            return createStatus(Change.Type.MODIFICATION, path, null)
          }
        }
      }
      return statuses[0]
    }

    return if (types.contains(Change.Type.DELETED)) createStatus(Change.Type.DELETED, path, null)
    else createStatus(Change.Type.MODIFICATION, path, null)
  }

  private fun getPath(info: S): Path? {
    when (getType(info)) {
      Change.Type.MODIFICATION, Change.Type.NEW, Change.Type.DELETED -> return getFirstPath(info)
      Change.Type.MOVED -> return getSecondPath(info)
    }
  }

  protected abstract fun createStatus(type: Change.Type, path: Path, secondPath: Path?): S

  protected abstract fun getFirstPath(info: S): Path

  protected abstract fun getSecondPath(info: S): Path?

  protected abstract fun getType(info: S): Change.Type

  class MergedStatusInfo<S> @JvmOverloads constructor(val statusInfo: S, infos: List<S> = ContainerUtil.emptyList()) {
    val mergedStatusInfos: List<S> = SmartList(infos)

    override fun toString(): String {
      return "MergedStatusInfo{" +
             "myStatusInfo=" + statusInfo +
             ", myMergedStatusInfos=" + mergedStatusInfos +
             '}'.toString()
    }
  }
}

class VcsFileStatusInfoMerger : VcsStatusMerger<VcsFileStatusInfo, CharSequence>() {
  override fun createStatus(type: Change.Type, path: CharSequence, secondPath: CharSequence?): VcsFileStatusInfo {
    return VcsFileStatusInfo(type, path, secondPath)
  }

  override fun getFirstPath(info: VcsFileStatusInfo): CharSequence = info.first

  override fun getSecondPath(info: VcsFileStatusInfo): CharSequence? = info.second

  override fun getType(info: VcsFileStatusInfo): Change.Type = info.type
}

abstract class VcsChangesMerger : VcsStatusMerger<Change, FilePath>() {
  override fun createStatus(type: Change.Type, path: FilePath, secondPath: FilePath?): Change {
    return when (type) {
      Change.Type.NEW -> createChange(type, null, path)
      Change.Type.DELETED -> createChange(type, path, null)
      Change.Type.MOVED -> createChange(type, path, secondPath)
      Change.Type.MODIFICATION -> createChange(type, path, path)
    }
  }

  protected abstract fun createChange(type: Change.Type, beforePath: FilePath?, afterPath: FilePath?): Change

  fun mergedChange(path: FilePath, changesToParents: List<Change>): MergedChange {
    return MergedChange.SimpleMergedChange(merge(path, changesToParents), changesToParents)
  }

  override fun getFirstPath(info: Change): FilePath {
    return when (info.type) {
      Change.Type.MODIFICATION, Change.Type.NEW -> info.afterRevision!!.file
      Change.Type.DELETED, Change.Type.MOVED -> info.beforeRevision!!.file
    }
  }

  override fun getSecondPath(info: Change): FilePath? {
    return when (info.type) {
      Change.Type.MOVED -> info.afterRevision!!.file
      else -> null
    }
  }

  override fun getType(info: Change): Change.Type = info.type
}