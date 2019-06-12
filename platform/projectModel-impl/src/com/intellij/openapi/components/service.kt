// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components

import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.project.Project
import com.intellij.project.ProjectStoreOwner

inline fun <reified T : Any> service(): T = ServiceManager.getService(T::class.java)

inline fun <reified T : Any> serviceOrNull(): T? = ServiceManager.getService(T::class.java)

inline fun <reified T : Any> serviceIfCreated(): T? = ServiceManager.getServiceIfCreated(T::class.java)

inline fun <reified T : Any> Project.service(): T = ServiceManager.getService(this, T::class.java)

val ComponentManager.stateStore: IComponentStore
  get() {
    return when {
      this is ProjectStoreOwner -> (this as ProjectStoreOwner).getComponentStore()
      else -> {
        // module or application service
        picoContainer.getComponentInstance(IComponentStore::class.java.name) as IComponentStore
      }
    }
  }

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun <T> ComponentManager.getComponents(baseClass: Class<T>): List<T> {
  return (this as ComponentManagerImpl).getComponentInstancesOfType(baseClass)
}