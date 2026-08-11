// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.jetbrains.annotations.ApiStatus

/**
 * Provides access to module-level services persistent components.
 *
 * This API allows migrating module-level services which implements `PersistentStateComponent`
 * to project-level services, while storing data in `*.iml` file the same way it was stored before.
 *
 * Suppose you have the following module-level service.
 *
 * ```kotlin
 * @State(name = "MyModuleServiceState")
 * class MyModuleService : PersistentStateComponent<MyState> {
 *   var state: MyState = MyState()
 *
 *   override fun getState(): MyState {
 *     return state
 *   }
 *   override fun loadState(value: MyState) {
 *     state = value
 *   }
 * }
 * ```
 * After migrating it to this API it would look like this.
 *
 * ```kotlin
 * const val COMPONENT_NAME = "MyModuleServiceState"
 * class MyModuleService(private val module: Module) {

 *   val componentService = CustomImlComponentService.getInstance(module.project)
 *
 *   fun getState(): MyState? {
 *     return componentService.getComponentValue<MyState>(module, COMPONENT_NAME)
 *   }
 *
 *   fun setState(value: MyState) {
 *     WriteAction.run<Throwable> {
 *       componentService.setComponentValueBlocking(module, COMPONENT_NAME, value)
 *     }
 *   }
 * }
 * ```

 * Also note that for [CustomImlComponentService] to work, the [com.intellij.platform.workspace.jps.serialization.CustomImlComponentNameContributor] extension point must be implemented.
 */
@ApiStatus.Experimental
interface CustomImlComponentService {
  /**
   *  Returns the deserialized value of a persistent component, or `null` if the component is not stored for the module.
   */
  fun <T> getComponentValue(module: Module, componentName: String, componentClass: Class<T>): T?

  /**
   * **Asynchronously** serializes and stores a persistent component value for the given module.
   */
  suspend fun <T> setComponentValue(module: Module, componentName: String, component: T)

  /**
   * Serializes and stores a persistent component value for the given module. Requires write action.
   */
  @RequiresWriteLock
  fun <T> setComponentValueBlocking(module: Module, componentName: String, component: T)

  companion object {
    @JvmStatic
    fun getInstance(project: Project): CustomImlComponentService {
      return project.service<CustomImlComponentService>()
    }
  }
}

/** Convenience overload for [getComponentValue]. */
inline fun <reified T> CustomImlComponentService.getComponentValue(module: Module, componentName: String): T? {
  return getComponentValue(module, componentName, T::class.java)
}