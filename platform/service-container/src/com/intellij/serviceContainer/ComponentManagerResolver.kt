// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer

import com.intellij.platform.instanceContainer.instantiation.ArgumentSupplier
import com.intellij.platform.instanceContainer.instantiation.DependencyResolver
import com.intellij.platform.instanceContainer.internal.isStatic
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
    return !constructor.isAnnotationPresent(NonInjectable::class.java) && !constructor.isAnnotationPresent(Deprecated::class.java)
  }

  override fun isInjectable(parameterType: Class<*>): Boolean = !isNotApplicableClass(parameterType)

  override fun resolveDependency(parameterType: Class<*>, round: Int): ArgumentSupplier? {
    componentManager.getInstanceHolder(keyClass = parameterType)
      ?.takeUnless {
        round == 0 && !it.isStatic() // on round zero only try statically registered instances
      }
      ?.let { holder ->
        return ArgumentSupplier {
          holder.getInstanceInCallerContext(keyClass = parameterType)
        }
      }

    if (round > 1) {
      LOG.error(doNotUseConstructorInjectionsMessage("requestorClass=${parameterType.name}, extensionClass=${parameterType.name}"))
    }
    return null
  }
}
