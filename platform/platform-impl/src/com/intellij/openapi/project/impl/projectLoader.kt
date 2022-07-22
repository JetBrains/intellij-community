// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ProjectLoadHelper")
@file:ApiStatus.Internal
package com.intellij.openapi.project.impl

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

internal fun isCorePlugin(descriptor: PluginDescriptor): Boolean {
  val id = descriptor.pluginId
  return id == PluginManagerCore.CORE_ID ||
         // K/N Platform Deps is a repackaged Java plugin
         id.idString == "com.intellij.kotlinNative.platformDeps"
}

@TestOnly
interface ProjectServiceContainerCustomizer {
  companion object {
    @TestOnly
    fun getEp(): ExtensionPointImpl<ProjectServiceContainerCustomizer> {
      return (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
        .getExtensionPoint("com.intellij.projectServiceContainerCustomizer")
    }
  }

  /**
   * Invoked after implementation classes for project's components were determined (and loaded),
   * but before components are instantiated.
   */
  fun serviceRegistered(project: Project)
}

/**
 * Usage requires IJ Platform team approval (including plugin into white-list).
 */
@ApiStatus.Internal
interface ProjectServiceContainerInitializedListener {
  /**
   * Invoked after implementation classes for project's components were determined (and loaded),
   * but before components are instantiated.
   */
  suspend fun serviceCreated(project: Project)
}