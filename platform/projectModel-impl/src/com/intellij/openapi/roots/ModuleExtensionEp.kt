// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginAware
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.module.Module
import com.intellij.util.xmlb.annotations.Attribute
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

/**
 * Provides a way to register an implementation of [ModuleExtension] without using data from the workspace model.
 * 
 * In new code, it's better to create new child entities for [com.intellij.platform.workspace.jps.entities.ModuleEntity] to store
 * data associated with a specific module. 
 * Existing implementations of [ModuleExtension] can be migrated to take data from the workspace model and keep the old API by using 
 * [com.intellij.workspaceModel.ide.legacyBridge.ModuleExtensionBridgeFactory].
 */
class ModuleExtensionEp @ApiStatus.Internal constructor(): PluginAware {
  private var pluginDescriptor: PluginDescriptor? = null

  @Attribute("implementation")
  @JvmField
  var implementationClass: String? = null
  
  private val constructorAndArgs: Pair<MethodHandle, Int> by lazy(LazyThreadSafetyMode.PUBLICATION) {
    val implementationClass = implementationClass ?: error("'implementation' attribute is not specified'")
    val pluginDescriptor = pluginDescriptor ?: error("plugin descriptor is not set")
    val instanceClass = ApplicationManager.getApplication().loadClass<ModuleExtension>(implementationClass, pluginDescriptor)
    val lookup = MethodHandles.privateLookupIn(instanceClass, methodLookup)
    try {
      return@lazy lookup.findConstructor(instanceClass, emptyConstructorMethodType) to 0
    }
    catch (_: NoSuchMethodException) {
      try {
        return@lazy lookup.findConstructor(instanceClass, moduleMethodType) to 1
      }
      catch (t: NoSuchMethodException) {
        throw PluginException("Failed to instantiate $implementationClass: it mush have either no-arg constructor or constructor taking Module as an argument",
                              t, pluginDescriptor.pluginId)
      }
    }
  }

  override fun setPluginDescriptor(pluginDescriptor: PluginDescriptor) {
    this.pluginDescriptor = pluginDescriptor
  }

  @ApiStatus.Internal
  fun createInstance(module: Module): ModuleExtension {
    try {
      val (constructor, argsCount) = constructorAndArgs
      return when (argsCount) {
        0 -> constructor.invoke() as ModuleExtension
        1 -> constructor.invoke(module) as ModuleExtension
        else -> error("Unexpected number of arguments: $argsCount")
      }
    }
    catch (e: PluginException) {
      throw e
    }
    catch (t: Throwable) {
      throw PluginException("Failed to instantiate ModuleExtension ($implementationClass)", t, pluginDescriptor?.pluginId)
    }
  }
}

private val methodLookup = MethodHandles.lookup()
private val emptyConstructorMethodType: MethodType = MethodType.methodType(Void.TYPE)
private val moduleMethodType = MethodType.methodType(Void.TYPE, Module::class.java)
