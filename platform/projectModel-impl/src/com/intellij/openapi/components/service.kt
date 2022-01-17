// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IComponentStoreOwner
import com.intellij.openapi.project.Project

inline fun <reified T : Any> service(): T {
  val serviceClass = T::class.java
  return ApplicationManager.getApplication().getService(serviceClass)
         ?: throw RuntimeException("Cannot find service ${serviceClass.name} (classloader=${serviceClass.classLoader})")
}

inline fun <reified T : Any> serviceOrNull(): T? = ApplicationManager.getApplication().getService(T::class.java)

inline fun <reified T : Any> serviceIfCreated(): T? = ApplicationManager.getApplication().getServiceIfCreated(T::class.java)

inline fun <reified T : Any> services(includeLocal: Boolean): List<T> = ApplicationManager.getApplication().getServices(T::class.java, includeLocal)

inline fun <reified T : Any> Project.service(): T = getService(T::class.java)

inline fun <reified T : Any> Project.serviceIfCreated(): T? = getServiceIfCreated(T::class.java)

inline fun <reified T : Any> Project.services(includeLocal: Boolean): List<T> = getServices(T::class.java, includeLocal)

val ComponentManager.stateStore: IComponentStore
  get() {
    return when (this) {
      is IComponentStoreOwner -> this.componentStore
      else -> {
        getService(IComponentStore::class.java)
      }
    }
  }