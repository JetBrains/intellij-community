// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.configurationStore.saveSettingsForRemoteDevelopment
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.DoNotAskAppManager
import com.intellij.notification.DoNotAskProjectManager
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project
import com.intellij.util.application
import kotlinx.coroutines.CoroutineScope

@State(name = "DoNotAskAppManager", storages = [Storage(value = "doNotAskNotifications.xml")])
internal class DoNotAskAppManagerImpl(private val cs: CoroutineScope) :
  DoNotAskSettings(), DoNotAskAppManager {

  override fun saveSettingsForRemDev() {
    saveSettingsForRemoteDevelopment(cs, application)
  }

  override fun getPropertiesComponent() : PropertiesComponent {
    return PropertiesComponent.getInstance()
  }

}

@State(name = "DoNotAskProjectManager", storages = [Storage(value = "doNotAskNotifications.xml")], useLoadedStateAsExisting = false)
internal class DoNotAskProjectManagerImpl(private val project: Project, private val cs: CoroutineScope) :
  DoNotAskSettings(), DoNotAskProjectManager {

  override fun saveSettingsForRemDev() {
    saveSettingsForRemoteDevelopment(cs, project)
  }

  override fun getPropertiesComponent() : PropertiesComponent {
    return PropertiesComponent.getInstance(project)
  }

}


