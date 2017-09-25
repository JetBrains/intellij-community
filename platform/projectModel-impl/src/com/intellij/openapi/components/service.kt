/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.components

import com.intellij.openapi.components.impl.ComponentManagerImpl
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.project.Project

inline fun <reified T : Any> service(): T = ServiceManager.getService(T::class.java)
inline fun <reified T : Any> Project.service(): T = ServiceManager.getService(this, T::class.java)

val ComponentManager.stateStore: IComponentStore
  get() {
    val key: Any = if (this is Project) IComponentStore::class.java else IComponentStore::class.java.name
    return picoContainer.getComponentInstance(key) as IComponentStore
  }

@Suppress("EXTENSION_SHADOWED_BY_MEMBER")
fun <T> ComponentManager.getComponents(baseClass: Class<T>): List<T> =
  (this as ComponentManagerImpl).getComponentInstancesOfType(baseClass)