// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.impl.stores.IComponentStore
import com.intellij.openapi.components.impl.stores.IComponentStoreOwner
import com.intellij.openapi.project.Project

/**
 * This is primarily intended to be used by the service implementation. When introducing a new service,
 * please add a static `getInstance()` method. For better tooling performance, it is always advised
 * to keep an explicit method return type.
 *
 *     @Service
 *     class MyApplicationService {
 *       companion object {
 *         @JvmStatic
 *         fun getInstance(): MyApplicationService = service()
 *       }
 *     }
 *
 * Using a `getInstance()` method is preferred over a property, because:
 *
 *   - It makes it more clear on the call site that it can involve loading the service, which might not be cheap.
 *
 *   - Loading the service can throw an exception, and having an exception thrown by a method call is less surprising
 *     than if it was caused by property access.
 *
 *   - (Over-)using properties may be error-prone in a way that it might be accidentally changed to a property with an initializer
 *     instead of the correct (but more verbose) property with a getter, and that change can easily be overlooked.
 *
 *   - Using the method instead of a property keeps `MyApplicationService.getInstance()` calls consistent
 *     when used both in Kotlin, and Java.
 *
 *   - Using the method keeps `MyApplicationService.getInstance()` consistent with `MyProjectService.getInstance(project)`,
 *     both on the declaration and call sites.
 */
inline fun <reified T : Any> service(): T {
  val serviceClass = T::class.java
  return ApplicationManager.getApplication().getService(serviceClass)
         ?: throw RuntimeException("Cannot find service ${serviceClass.name} (classloader=${serviceClass.classLoader})")
}

inline fun <reified T : Any> serviceOrNull(): T? = ApplicationManager.getApplication().getService(T::class.java)

inline fun <reified T : Any> serviceIfCreated(): T? = ApplicationManager.getApplication().getServiceIfCreated(T::class.java)

inline fun <reified T : Any> services(includeLocal: Boolean): List<T> = ApplicationManager.getApplication().getServices(T::class.java, includeLocal)

/**
 * This is primarily intended to be used by the service implementation. When introducing a new service,
 * please add a static `getInstance(Project)` method. For better tooling performance, it is always advised
 * to keep an explicit method return type.
 *
 *     @Service
 *     class MyProjectService(private val project: Project) {
 *       companion object {
 *         @JvmStatic
 *         fun getInstance(project: Project): MyProjectService = project.service()
 *       }
 *     }
 *
 */
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