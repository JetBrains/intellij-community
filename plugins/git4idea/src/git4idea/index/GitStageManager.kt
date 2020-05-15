// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManagerListener
import git4idea.GitVcs

class GitStageManager(val project: Project) : Disposable {

  fun installListeners() {
    stageRegistryOption().addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        if (isStageAvailable(project)) {
          GitStageTracker.getInstance(project).scheduleUpdateAll()
        }
        project.messageBus.syncPublisher(ChangesViewContentManagerListener.TOPIC).toolWindowMappingChanged()
      }
    }, this)
  }

  override fun dispose() {
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project) = project.getService(GitStageManager::class.java)
  }
}

class GitStageStartupActivity : StartupActivity.Background {
  override fun runActivity(project: Project) {
    if (isStageAvailable(project)) {
      GitStageTracker.getInstance(project).scheduleUpdateAll()
    }
    GitStageManager.getInstance(project).installListeners()
  }
}

fun stageRegistryOption() = Registry.get("git.enable.stage")

fun isStageAvailable(project: Project): Boolean {
  return stageRegistryOption().asBoolean() &&
         ProjectLevelVcsManager.getInstance(project).allVcsRoots.any { it.vcs?.keyInstanceMethod == GitVcs.getKey() }
}