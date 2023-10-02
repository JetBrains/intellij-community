// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.platform.instanceContainer.internal.isStatic
import com.intellij.platform.instanceContainer.instantiation.ArgumentSupplier
import com.intellij.platform.instanceContainer.instantiation.DependencyResolver
import java.lang.Deprecated
import java.lang.reflect.Constructor
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.let
import kotlin.takeUnless

internal class ComponentManagerResolver(
  private val componentManager: ComponentManagerImpl,
) : DependencyResolver {

  override fun toString(): String = "Dependency resolver from $componentManager"

  override fun isApplicable(constructor: Constructor<*>): Boolean {
    return !constructor.isAnnotationPresent(NonInjectable::class.java) &&
           !constructor.isAnnotationPresent(Deprecated::class.java)
  }

  override fun isInjectable(parameterType: Class<*>): Boolean {
    return !isNotApplicableClass(parameterType)
  }

  override fun resolveDependency(parameterType: Class<*>, round: Int): ArgumentSupplier? {
    componentManager.getInstanceHolder(keyClass = parameterType)
      ?.takeUnless {
        round == 0 && !it.isStatic() // on round zero only try statically registered instances
      }
      ?.let { holder ->
        return ArgumentSupplier {
          holder.getInstanceInCallerDispatcher(keyClass = parameterType)
        }
      }

    if (round > 1) {
      //if (componentManager.isGetComponentAdapterOfTypeCheckEnabled) {
      //  LOG.error(
      //    PluginException("getComponentAdapterOfType is used to get ${expectedType.name} (requestorClass=${requestorClass.name}, requestorConstructor=${requestorConstructor})." +
      //                    "\n\nProbably constructor should be marked as NonInjectable.", pluginId))
      //}
      componentManager.getHolderOfType(parameterType)?.let {
        return ArgumentSupplier {
          it.getInstanceInCallerDispatcher(keyClass = null)
        }
      }

      val extension = componentManager.extensionArea.findExtensionByClass(parameterType)
                      ?: return null
      //val message = doNotUseConstructorInjectionsMessage("requestorClass=${parameterType.name}, extensionClass=${expectedType.name}")
      //val app = componentManager.getApplication()
      //@Suppress("SpellCheckingInspection")
      //if (app != null && app.isUnitTestMode && pluginId.idString != "org.jetbrains.kotlin" && pluginId.idString != "Lombook Plugin") {
      //  throw PluginException(message, pluginId)
      //}
      //else {
      //  LOG.warn(message)
      //}
      return ArgumentSupplier {
        extension
      }
    }
    return null
    //val className = expectedType.name
    //if (componentManager.parent == null) {
    //  if (badAppLevelClasses.contains(className)) {
    //    return null
    //  }
    //}
    //else if (className == "com.intellij.configurationStore.StreamProvider" ||
    //         className == "com.intellij.openapi.roots.LanguageLevelModuleExtensionImpl" ||
    //         className == "com.intellij.openapi.roots.impl.CompilerModuleExtensionImpl" ||
    //         className == "com.intellij.openapi.roots.impl.JavaModuleExternalPathsImpl") {
    //  return null
    //}

    //return componentManager.getComponentAdapterOfType(expectedType) ?: componentManager.parent?.getComponentAdapterOfType(expectedType)
    //return null
  }
}
