// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.platform.vcs.impl.shared.rpc.ChangeId
import org.jetbrains.annotations.ApiStatus

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class ChangeListChangeIdCache {
  @Volatile
  private var cachedChanges: Map<ChangeId, Change> = emptyMap()

  fun getChange(id: ChangeId): Change? = cachedChanges[id]

  fun updateCache(allChanges: Iterable<Set<Change>>) {
    cachedChanges = allChanges.asSequence().flatten().associateBy { ChangeId.getId(it) }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ChangeListChangeIdCache = project.service()
  }
}