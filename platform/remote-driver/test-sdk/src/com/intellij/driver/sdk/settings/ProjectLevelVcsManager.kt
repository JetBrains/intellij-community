package com.intellij.driver.sdk.settings

import com.intellij.driver.client.Driver
import com.intellij.driver.client.Remote
import com.intellij.driver.client.service
import com.intellij.driver.model.RdTarget
import com.intellij.driver.sdk.Project
import com.intellij.driver.sdk.getOpenProjects
import com.intellij.driver.sdk.waitFor
import com.intellij.driver.sdk.withoutPauseOnIndicators
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes


fun Driver.getProjectLevelVcsManagerInstance(project: Project) = utility(ProjectLevelVcsManager::class).getInstance(project)

/**
 * Waits until the backend activates the VCS of each open project.
 * A post-startup activity does this, also in a project without VCS.
 * The IDE shows a VCS tool window only after this activation.
 */
fun Driver.waitForVcsActivated(timeout: Duration = 1.minutes): Unit = withoutPauseOnIndicators {
  waitFor("The VCS of each open project is activated", timeout) {
    getOpenProjects(RdTarget.BACKEND).all { project ->
      service<ProjectLevelVcsManager>(project, RdTarget.BACKEND).areVcsesActivated()
    }
  }
}

@Remote("com.intellij.openapi.vcs.ProjectLevelVcsManager", plugin = "com.intellij.platform.vcs/intellij.platform.vcs")
interface ProjectLevelVcsManager {
  fun getInstance(project: Project): ProjectLevelVcsManager
  fun getDirectoryMappings(): List<VcsDirectoryMapping>
  fun setDirectoryMappings(mappings: List<VcsDirectoryMapping>)
  fun areVcsesActivated(): Boolean
}

@Remote("com.intellij.openapi.vcs.VcsDirectoryMapping", plugin = "com.intellij.platform.vcs/intellij.platform.vcs")
interface VcsDirectoryMapping {
  fun getDirectory(): String
  fun getVcs(): String
}
