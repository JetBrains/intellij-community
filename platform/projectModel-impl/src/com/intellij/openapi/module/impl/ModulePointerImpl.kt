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
package com.intellij.openapi.module.impl

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModulePointer
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read

private val LOG = Logger.getInstance(ModulePointerImpl::class.java)

class ModulePointerImpl : ModulePointer {
  private var module: Module? = null
  private var moduleName: String? = null
  private val lock: ReentrantReadWriteLock

  internal constructor(module: Module, lock: ReentrantReadWriteLock) {
    this.module = module
    this.lock = lock
  }

  internal constructor(moduleName: String, lock: ReentrantReadWriteLock) {
    this.moduleName = moduleName
    this.lock = lock
  }

  override fun getModule() = lock.read { module }

  override fun getModuleName(): String = lock.read { module?.name ?: moduleName!! }

  // must be called under lock, so, explicit lock using is not required
  internal fun moduleAdded(module: Module) {
    LOG.assertTrue(moduleName == module.name)
    moduleName = null
    this.module = module
  }

  // must be called under lock, so, explicit lock using is not required
  internal fun moduleRemoved(module: Module) {
    val resolvedModule = this.module
    LOG.assertTrue(resolvedModule === module)
    moduleName = resolvedModule!!.name
    this.module = null
  }

  internal fun renameUnresolved(newName: String) {
    LOG.assertTrue(module == null)
    moduleName = newName
  }

  override fun toString() = "moduleName: $moduleName, module: $module"
}
