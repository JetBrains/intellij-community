package com.intellij.remoteDev.util.tests

import com.intellij.openapi.application.Application
import com.intellij.openapi.project.Project
import com.intellij.remoteDev.util.tests.modelGenerated.RdAgentId
import com.jetbrains.rd.framework.IProtocol
import org.jetbrains.annotations.ApiStatus

/**
 * Provides access to all essential entities on this agent required to perform test operations
 */
@ApiStatus.Internal
class AgentContext(
  val agentId: RdAgentId,
  val application: Application,
  private val thisProject: Project?,
  val protocol: IProtocol
) {
  val project: Project
    get() {
      if (thisProject != null) return thisProject
      else {
        error("Project shouldn't be requested for the projectless application")
      }
    }
}
