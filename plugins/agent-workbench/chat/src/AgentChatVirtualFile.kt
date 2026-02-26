// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.chat

import com.intellij.agent.workbench.common.AgentThreadActivity
import com.intellij.agent.workbench.sessions.core.AgentSessionProvider
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.vfs.VirtualFileSystem
import com.intellij.testFramework.LightVirtualFile
import org.jetbrains.annotations.TestOnly

private class AgentChatVirtualFileLog

private val LOG = logger<AgentChatVirtualFileLog>()

internal class AgentChatVirtualFile internal constructor(
  private val fileSystem: AgentChatVirtualFileSystem,
  resolution: AgentChatTabResolution,
) : LightVirtualFile(resolveFileName(resolution.tabKey.value)) {
  private val key: AgentChatTabKey = resolution.tabKey

  val tabKey: String
    get() = key.value

  var projectHash: String = ""
    private set

  var projectPath: String = ""
    private set

  var threadIdentity: String = ""
    private set

  var provider: AgentSessionProvider? = null
    private set

  var sessionId: String = ""
    private set

  var isPendingThread: Boolean = false
    private set

  var subAgentId: String? = null
    private set

  var shellCommand: List<String> = emptyList()
    private set

  var threadId: String = ""
    private set

  var threadTitle: String = resolveThreadTitle("")
    private set

  var threadActivity: AgentThreadActivity = AgentThreadActivity.READY
    private set

  @TestOnly
  internal constructor(
    projectPath: String,
    threadIdentity: String,
    shellCommand: List<String>,
    threadId: String,
    threadTitle: String,
    subAgentId: String?,
    threadActivity: AgentThreadActivity = AgentThreadActivity.READY,
    projectHash: String = "",
  ) : this(
    fileSystem = createStandaloneAgentChatVirtualFileSystemForTest(),
    resolution = AgentChatTabResolution.Resolved(AgentChatTabSnapshot.create(
      projectHash = projectHash,
      projectPath = projectPath,
      threadIdentity = threadIdentity,
      threadId = threadId,
      threadTitle = threadTitle,
      subAgentId = subAgentId,
      shellCommand = shellCommand,
      threadActivity = threadActivity,
    ))
  )


  init {
    updateFromResolution(resolution)
    isWritable = false
  }

  override fun getFileSystem(): VirtualFileSystem = fileSystem

  override fun getPath(): String = key.toPath()

  fun matches(threadIdentity: String, subAgentId: String?): Boolean {
    return this.threadIdentity == threadIdentity && this.subAgentId == subAgentId
  }

  fun updateThreadTitle(threadTitle: String): Boolean {
    val resolvedTitle = resolveThreadTitle(threadTitle)
    if (this.threadTitle == resolvedTitle) {
      LOG.debug {
        "Skipped tab title update(identity=$threadIdentity, subAgentId=$subAgentId): unchanged title=$resolvedTitle"
      }
      return false
    }

    val oldTitle = this.threadTitle
    this.threadTitle = resolvedTitle
    LOG.debug {
      "Updated tab title(identity=$threadIdentity, subAgentId=$subAgentId): oldTitle=$oldTitle newTitle=$resolvedTitle"
    }
    return true
  }

  fun updateThreadActivity(threadActivity: AgentThreadActivity): Boolean {
    if (this.threadActivity == threadActivity) {
      LOG.debug {
        "Skipped tab activity update(identity=$threadIdentity, subAgentId=$subAgentId): unchanged activity=$threadActivity"
      }
      return false
    }

    val oldActivity = this.threadActivity
    this.threadActivity = threadActivity
    LOG.debug {
      "Updated tab activity(identity=$threadIdentity, subAgentId=$subAgentId): oldActivity=$oldActivity newActivity=$threadActivity"
    }
    return true
  }

  fun updateCommandAndThreadId(shellCommand: List<String>, threadId: String) {
    this.shellCommand = shellCommand
    this.threadId = threadId
  }

  fun rebindPendingThread(
    threadIdentity: String,
    shellCommand: List<String>,
    threadId: String,
    threadTitle: String,
    threadActivity: AgentThreadActivity,
  ): Boolean {
    var changed = false
    if (this.threadIdentity != threadIdentity) {
      this.threadIdentity = threadIdentity
      updateThreadCoordinates()
      changed = true
    }
    if (this.shellCommand != shellCommand || this.threadId != threadId) {
      updateCommandAndThreadId(shellCommand = shellCommand, threadId = threadId)
      changed = true
    }
    if (updateThreadTitle(threadTitle)) {
      changed = true
    }
    if (updateThreadActivity(threadActivity)) {
      changed = true
    }

    if (changed) {
      LOG.debug {
        "Rebound pending tab(identity=$threadIdentity, subAgentId=$subAgentId, threadId=$threadId)"
      }
    }
    return changed
  }

  internal fun updateFromResolution(resolution: AgentChatTabResolution) {
    when (resolution) {
      is AgentChatTabResolution.Resolved -> updateFromSnapshot(resolution.snapshot)
      is AgentChatTabResolution.Unresolved -> Unit
    }
  }

  private fun updateFromSnapshot(snapshot: AgentChatTabSnapshot) {
    if (snapshot.identity.threadIdentity.isNotBlank() || snapshot.identity.projectPath.isNotBlank()) {
      projectHash = snapshot.identity.projectHash
      projectPath = snapshot.identity.projectPath
      threadIdentity = snapshot.identity.threadIdentity
      subAgentId = snapshot.identity.subAgentId
      updateThreadCoordinates()
    }
    if (snapshot.runtime.threadId.isNotBlank() || snapshot.runtime.shellCommand.isNotEmpty()) {
      updateCommandAndThreadId(shellCommand = snapshot.runtime.shellCommand, threadId = snapshot.runtime.threadId)
    }
    if (snapshot.runtime.threadTitle.isNotBlank()) {
      updateThreadTitle(snapshot.runtime.threadTitle)
    }
    updateThreadActivity(snapshot.runtime.threadActivity)
  }

  private fun updateThreadCoordinates() {
    val coordinates = resolveAgentChatThreadCoordinates(threadIdentity)
    provider = coordinates?.provider
    sessionId = coordinates?.sessionId.orEmpty()
    isPendingThread = coordinates?.isPending ?: false
  }

  internal fun toSnapshot(): AgentChatTabSnapshot {
    return AgentChatTabSnapshot(
      tabKey = key,
      identity = AgentChatTabIdentity(
        projectHash = projectHash,
        projectPath = projectPath,
        threadIdentity = threadIdentity,
        subAgentId = subAgentId,
      ),
      runtime = AgentChatTabRuntime(
        threadId = threadId,
        threadTitle = threadTitle,
        shellCommand = shellCommand,
        threadActivity = threadActivity,
      ),
    )
  }
}

private fun resolveFileName(tabKey: String): String {
  return "chat-$tabKey"
}

private fun resolveThreadTitle(threadTitle: String): String {
  return threadTitle.takeIf { it.isNotBlank() } ?: AgentChatBundle.message("chat.filetype.name")
}
