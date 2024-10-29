// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.table.links

import com.intellij.openapi.components.serviceOrNull
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.LinkDescriptor
import com.intellij.util.messages.Topic
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.visible.VisiblePack
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Experimental
interface CommitLinksProvider {
  /**
   * Return cached [LinkDescriptor] for the given [CommitId].
   */
  fun getLinks(commitId: CommitId): List<LinkDescriptor>

  /**
   * Asynchronously search in the given commits for links and cache it.
   *
   * E.g., in the Git VCS it could be "fixup!", "squash!" and "amend!" prefixes in the commit message subject.
   */
  fun resolveLinks(logId: String, logData: VcsLogData, visiblePack: VisiblePack,
                   startRow: Int, endRow: Int)

  companion object {
    @JvmStatic
    fun getServiceOrNull(project: Project) = project.serviceOrNull<CommitLinksProvider>()
  }
}

@ApiStatus.Experimental
fun interface CommitLinksResolveListener : EventListener {
  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC = Topic(CommitLinksResolveListener::class.java, Topic.BroadcastDirection.NONE)
  }

  fun onLinksResolved(logId: String)
}
