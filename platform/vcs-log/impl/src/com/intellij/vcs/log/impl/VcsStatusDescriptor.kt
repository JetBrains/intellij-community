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

import com.intellij.openapi.vcs.changes.Change
import com.intellij.util.SmartList
import com.intellij.util.containers.ContainerUtil

abstract class VcsStatusDescriptor<S> {
  fun getMergedStatusInfo(statuses: List<List<S>>): List<MergedStatusInfo<S>> {
    statuses.singleOrNull()?.let { return it.map { MergedStatusInfo(it) } }

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

      result.add(MergedStatusInfo(getMergedStatusInfo(path, statusesList), statusesList))
    }

    return result
  }

  private fun getMergedStatusInfo(path: String, statuses: List<S>): S {
    val types = statuses.map { getType(it) }.distinct()

    if (types.size == 1) {
      if (types.single() == Change.Type.MOVED) {
        var renamedFrom: String? = null
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

  private fun getPath(info: S): String? {
    when (getType(info)) {
      Change.Type.MODIFICATION, Change.Type.NEW, Change.Type.DELETED -> return getFirstPath(info)
      Change.Type.MOVED -> return getSecondPath(info)
    }
  }

  protected abstract fun createStatus(type: Change.Type, path: String, secondPath: String?): S

  abstract fun getFirstPath(info: S): String

  abstract fun getSecondPath(info: S): String?

  abstract fun getType(info: S): Change.Type

  class MergedStatusInfo<S> @JvmOverloads constructor(val statusInfo: S, infos: List<S> = ContainerUtil.emptyList()) {
    val mergedStatusInfos: List<S>

    init {
      mergedStatusInfos = SmartList(infos)
    }

    override fun toString(): String {
      return "MergedStatusInfo{" +
             "myStatusInfo=" + statusInfo +
             ", myMergedStatusInfos=" + mergedStatusInfos +
             '}'.toString()
    }
  }
}

data class VcsFileStatusInfo(val type: Change.Type, val firstPath: String, val secondPath: String?) {
  override fun toString(): String {
    var s = type.toString() + " " + firstPath
    if (secondPath != null) {
      s += " -> $secondPath"
    }
    return s
  }
}

class VcsFileStatusInfoDescriptor : VcsStatusDescriptor<VcsFileStatusInfo>() {
  override fun createStatus(type: Change.Type, path: String, secondPath: String?): VcsFileStatusInfo {
    return VcsFileStatusInfo(type, path, secondPath)
  }

  override fun getFirstPath(info: VcsFileStatusInfo): String = info.firstPath

  override fun getSecondPath(info: VcsFileStatusInfo): String? = info.secondPath

  override fun getType(info: VcsFileStatusInfo): Change.Type = info.type
}