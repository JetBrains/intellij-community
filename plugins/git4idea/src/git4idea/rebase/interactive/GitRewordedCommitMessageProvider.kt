// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.annotations.XCollection
import git4idea.rebase.GitRebaseUtils

@State(
  name = "GitRewordedCommitMessages",
  storages = [Storage(file = StoragePathMacros.WORKSPACE_FILE, roamingType = RoamingType.DISABLED)],
  reportStatistic = false
)
internal class GitRewordedCommitMessageProvider :
  SimplePersistentStateComponent<GitRewordedCommitMessagesInfo>(GitRewordedCommitMessagesInfo()) {

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.service<GitRewordedCommitMessageProvider>()
  }

  fun save(project: Project, root: VirtualFile, mappings: List<RewordedCommitMessageMapping>) {
    val ontoHash = GitRebaseUtils.getOntoHash(project, root)?.asString() ?: return
    state.onto = ontoHash
    state.currentCommit = 0
    state.commitMessagesMapping.clear()
    state.commitMessagesMapping.addAll(mappings)
  }

  fun getRewordedCommitMessage(project: Project, root: VirtualFile, originalMessage: String): String? {
    if (!checkRebaseOnto(project, root)) {
      return null
    }
    val commitMappings = state.commitMessagesMapping
    val currentCommit = state.currentCommit.takeIf { it < commitMappings.size } ?: return null
    val mapping = commitMappings[currentCommit]
    val savedOriginalMessage = mapping.originalMessage ?: return null
    val rewordedMessage = mapping.rewordedMessage ?: return null

    return rewordedMessage.takeIf { originalMessage.startsWith(savedOriginalMessage) }?.also {
      state.currentCommit++
    }
  }

  private fun checkRebaseOnto(project: Project, root: VirtualFile): Boolean {
    val currentRebaseOntoHash = GitRebaseUtils.getOntoHash(project, root)?.asString() ?: return false
    val savedOntoHash = state.onto
    return currentRebaseOntoHash == savedOntoHash
  }
}

internal class GitRewordedCommitMessagesInfo : BaseState() {
  var onto by string()
  var currentCommit by property(0)

  @get:XCollection
  val commitMessagesMapping by list<RewordedCommitMessageMapping>()
}

internal class RewordedCommitMessageMapping : BaseState() {
  companion object {
    @JvmStatic
    fun fromMapping(originalMessage: String, rewordedMessage: String) = RewordedCommitMessageMapping().apply {
      this.originalMessage = originalMessage
      this.rewordedMessage = rewordedMessage
    }
  }

  var originalMessage by string()
  var rewordedMessage by string()
}