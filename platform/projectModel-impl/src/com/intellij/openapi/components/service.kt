// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components

import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.project.Project

inline fun <reified T : Any> service(): T = ServiceManager.getService(T::class.java)

inline fun <reified T : Any> serviceOrNull(): T? = ServiceManager.getService(T::class.java)

inline fun <reified T : Any> serviceIfCreated(): T? = ServiceManager.getServiceIfCreated(T::class.java)

inline fun <reified T : Any> Project.service(): T = ServiceManager.getService(this, T::class.java)

val ComponentManager.stateStore: IComponentStore
  get() {
    val key: Any = if (this is Project) IComponentStore::class.java else IComponentStore::class.java.name
    return picoContainer.getComponentInstance(key) as IComponentStore
  }

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun <T> ComponentManager.getComponents(baseClass: Class<T>): List<T> =
  (this as ComponentManagerImpl).getComponentInstancesOfType(baseClass)