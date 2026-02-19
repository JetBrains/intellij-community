// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.collectors.fus.ui

import com.intellij.internal.statistic.utils.getPluginInfoByDescriptor
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.util.application
import kotlinx.collections.immutable.toImmutableList
import java.util.concurrent.ConcurrentSkipListSet

@Service(Service.Level.APP)
internal class DialogInvocationPlacesCollector private constructor() {
  private val invocationPlaces: MutableSet<String> = ConcurrentSkipListSet()

  init {
    processExtensions()
  }

  companion object {
    @JvmStatic
    fun getInstance(): DialogInvocationPlacesCollector = application.service()
    private val EP_NAME: ExtensionPointName<DialogInvocationPlaceEP> = ExtensionPointName("com.intellij.dialogInvocationPlace")
  }

  private fun processExtensions() {
    EP_NAME.processWithPluginDescriptor { extension, pluginDescriptor ->
      addInvocationPlace(extension, pluginDescriptor)
    }
    EP_NAME.addExtensionPointListener(object : ExtensionPointListener<DialogInvocationPlaceEP> {
      override fun extensionAdded(extension: DialogInvocationPlaceEP, pluginDescriptor: PluginDescriptor) {
        addInvocationPlace(extension, pluginDescriptor)
      }
    }, null)
  }

  private fun addInvocationPlace(extension: DialogInvocationPlaceEP, pluginDescriptor: PluginDescriptor) {
    val info = getPluginInfoByDescriptor(pluginDescriptor)
    if (info.isSafeToReport() && extension.id != null) {
      invocationPlaces.add(extension.id!!)
    }
  }

  fun getInvocationPlaces(): List<String> = invocationPlaces.toImmutableList()
}