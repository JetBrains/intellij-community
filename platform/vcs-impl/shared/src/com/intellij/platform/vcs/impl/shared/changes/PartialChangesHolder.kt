// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.shared.changes

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ex.LocalRange
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.impl.shared.rpc.PartialChangesApi
import com.intellij.platform.vcs.impl.shared.rpc.PartialChangesEvent
import fleet.rpc.client.durable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import kotlin.time.Duration.Companion.milliseconds

/**
 * Holds the state of files' included/excluded ranges based on PartialLocalLineStatusTracker.
 * Unlike it doesn't provide consistency guarantees and should be used for UI purposes only.
 */
@Service(Service.Level.PROJECT)
@OptIn(FlowPreview::class)
@ApiStatus.Internal
class PartialChangesHolder(project: Project, cs: CoroutineScope) {
  private val mapping: MutableMap<FilePath, List<LocalRange>> = mutableMapOf()

  val updates: SharedFlow<Unit>

  init {
    val updatesFlow = MutableSharedFlow<Unit>()
    cs.launch {
      durable {
        mapping.clear()
        PartialChangesApi.getInstance().partialChanges(project.projectId()).collect { event ->
          LOG.trace { "New event - $event" }
          when (event) {
            is PartialChangesEvent.RangesUpdated -> {
              val path = event.file.filePath
              mapping[path] = event.ranges
            }
            is PartialChangesEvent.TrackerRemoved -> {
              val path = event.file.filePath
              mapping.remove(path)
            }
          }

          updatesFlow.emit(Unit)
        }
      }
    }

    updates = updatesFlow.debounce(100.milliseconds).shareIn(cs, SharingStarted.Lazily)
  }

  fun getRanges(filePath: FilePath): List<LocalRange>? = mapping[filePath]

  companion object {
    private val LOG = logger<PartialChangesHolder>()

    fun getInstance(project: Project): PartialChangesHolder = project.service()
  }
}