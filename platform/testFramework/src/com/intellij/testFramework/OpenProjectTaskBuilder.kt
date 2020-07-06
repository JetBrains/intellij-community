// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.project.impl.ProjectExImpl
import com.intellij.util.ThreeState

/**
 * Do not use in Kotlin, for Java only.
 */
class OpenProjectTaskBuilder {
  private var options = createTestOpenProjectOptions()
  private var runPostStartUpActivities = true
  private var componentStoreLoadingEnabled = ThreeState.UNSURE

  /**
   * Disabling running post start-up activities can speed-up test a little bit.
   */
  fun runPostStartUpActivities(value: Boolean): OpenProjectTaskBuilder {
    runPostStartUpActivities = value
    return this
  }

  fun projectName(value: String?): OpenProjectTaskBuilder {
    options = options.copy(projectName = value)
    return this
  }

  fun build(): OpenProjectTask {
    if (componentStoreLoadingEnabled == ThreeState.UNSURE && runPostStartUpActivities) {
      return options
    }

    return options.copy(beforeInit = { project ->
      if (!runPostStartUpActivities) {
        project.putUserData(ProjectExImpl.RUN_START_UP_ACTIVITIES, false)
      }
      if (componentStoreLoadingEnabled != ThreeState.UNSURE) {
        project.putUserData(IProjectStore.COMPONENT_STORE_LOADING_ENABLED, componentStoreLoadingEnabled.toBoolean())
      }
    })
  }

  fun runConfigurators(value: Boolean): OpenProjectTaskBuilder {
    options = options.copy(runConfigurators = value)
    return this
  }

  fun componentStoreLoadingEnabled(value: Boolean): OpenProjectTaskBuilder {
    componentStoreLoadingEnabled = ThreeState.fromBoolean(value)
    return this
  }
}

fun createTestOpenProjectOptions(runPostStartUpActivities: Boolean = true): OpenProjectTask {
  // In tests it is caller responsibility to refresh VFS (because often not only the project file must be refreshed, but the whole dir - so, no need to refresh several times).
  // Also, cleanPersistedContents is called on start test application.
  var task = OpenProjectTask(forceOpenInNewFrame = true,
                             isRefreshVfsNeeded = false,
                             runConversionBeforeOpen = false,
                             runConfigurators = false,
                             showWelcomeScreen = false,
                             useDefaultProjectAsTemplate = false)
  if (!runPostStartUpActivities) {
    task = task.copy(beforeInit = {
      it.putUserData(ProjectExImpl.RUN_START_UP_ACTIVITIES, false)
    })
  }
  return task
}