// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("ProjectLoadHelper")
@file:ApiStatus.Internal
package com.intellij.openapi.project.impl

import com.intellij.diagnostic.Activity
import com.intellij.diagnostic.PluginException
import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.diagnostic.StartUpMeasurer.Activities
import com.intellij.ide.plugins.IdeaPluginDescriptorImpl
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.impl.ExtensionPointImpl
import com.intellij.openapi.extensions.impl.ExtensionsAreaImpl
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

// Code maybe located in a ProjectImpl, but it is not possible due to non-technical reasons to convert ProjectImpl into modern language.
internal fun registerComponents(project: ProjectImpl) {
  var activity = createActivity(
    project) { "project ${Activities.REGISTER_COMPONENTS_SUFFIX}" }
  //  at this point of time plugins are already loaded by application - no need to pass indicator to getLoadedPlugins call
  @Suppress("UNCHECKED_CAST")
  project.registerComponents(PluginManagerCore.getLoadedPlugins() as List<IdeaPluginDescriptorImpl>)

  activity = activity?.endAndStart("projectComponentRegistered")
  runOnlyCorePluginExtensions(
    ProjectServiceContainerCustomizer.getEp()) {
    it.serviceRegistered(project)
  }
  activity?.end()
}

private inline fun createActivity(project: ProjectImpl, message: () -> String): Activity? {
  return if (project.isDefault || !StartUpMeasurer.isEnabled()) null else StartUpMeasurer.startActivity(message())
}

internal inline fun <T : Any> runOnlyCorePluginExtensions(ep: ExtensionPointImpl<T>, crossinline executor: (T) -> Unit) {
  ep.processWithPluginDescriptor(true) { handler, pluginDescriptor ->
    if (pluginDescriptor.pluginId != PluginManagerCore.CORE_ID) {
      logger<ProjectImpl>().error(PluginException("Plugin $pluginDescriptor is not approved to add ${ep.name}", pluginDescriptor.pluginId))
    }

    try {
      executor(handler)
    }
    catch (e: ProcessCanceledException) {
      throw e
    }
    catch (e: Throwable) {
      logger<ProjectImpl>().error(PluginException(e, pluginDescriptor.pluginId))
    }
  }
}

/**
 * Usage requires IJ Platform team approval (including plugin into white-list).
 */
@ApiStatus.Internal
interface ProjectServiceContainerCustomizer {
  companion object {
    @JvmStatic
    fun getEp() = (ApplicationManager.getApplication().extensionArea as ExtensionsAreaImpl)
      .getExtensionPoint<ProjectServiceContainerCustomizer>("com.intellij.projectServiceContainerCustomizer")
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
  fun serviceCreated(project: Project)
}