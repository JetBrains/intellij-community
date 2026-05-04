// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.codeSmells

import com.intellij.openapi.project.Project
import com.intellij.platform.rpc.topics.ProjectRemoteTopic
import com.intellij.platform.rpc.topics.ProjectRemoteTopicListener
import com.intellij.platform.vcs.impl.shared.CODE_SMELL_REMOTE_TOPIC
import com.intellij.platform.vcs.impl.shared.CodeSmellEvent
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class CodeSmellRemoteTopicListener : ProjectRemoteTopicListener<CodeSmellEvent> {
  override val topic: ProjectRemoteTopic<CodeSmellEvent> = CODE_SMELL_REMOTE_TOPIC

  override fun handleEvent(project: Project, event: CodeSmellEvent) {
    FrontendCodeSmellService.getInstance(project).showCodeSmellsInToolWindow(event.smells)
  }
}
