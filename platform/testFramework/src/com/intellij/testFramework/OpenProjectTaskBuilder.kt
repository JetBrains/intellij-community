// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.configurationStore.StoreReloadManager
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.components.impl.stores.IProjectStore
import com.intellij.openapi.components.impl.stores.stateStore
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.impl.ProjectImpl
import com.intellij.util.ThreeState
import java.util.function.Consumer

// todo rewrite PlatformTestUtil to kotlin
internal fun saveProject(project: Project, forceSavingAllSettings: Boolean = false) {
  runUnderModalProgressIfIsEdt {
    StoreReloadManager.getInstance(project).reloadChangedStorageFiles()
    project.stateStore.save(forceSavingAllSettings = forceSavingAllSettings)
  }
}

/**
 * Do not use in Kotlin, for Java only.
 */
class OpenProjectTaskBuilder {
  private var options = createTestOpenProjectOptions()
  private var runPostStartUpActivities = true
  private var componentStoreLoadingEnabled = ThreeState.UNSURE

  /**
   * Disabling running post start-up activities can speed up test a little.
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
        project.putUserData(ProjectImpl.RUN_START_UP_ACTIVITIES, false)
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

@JvmOverloads
fun createTestOpenProjectOptions(runPostStartUpActivities: Boolean = true, beforeOpen: Consumer<Project>? = null): OpenProjectTask {
  // In tests, it is caller responsibility to refresh VFS
  // (because often not only the project file must be refreshed, but the whole dir - so, no need to refresh several times).
  // Also, cleanPersistedContents is called on start test application.
  return OpenProjectTask {
    forceOpenInNewFrame = true

    runConversionBeforeOpen = false
    runConfigurators = false
    showWelcomeScreen = false
    useDefaultProjectAsTemplate = false
    if (beforeOpen != null) {
      this.beforeOpen = {
        beforeOpen.accept(it)
        true
      }
    }

    if (!runPostStartUpActivities) {
      beforeInit = {
        it.putUserData(ProjectImpl.RUN_START_UP_ACTIVITIES, false)
      }
    }
  }
}