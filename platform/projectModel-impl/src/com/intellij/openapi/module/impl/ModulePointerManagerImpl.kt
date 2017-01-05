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

import com.intellij.ProjectTopics
import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModulePointer
import com.intellij.openapi.module.ModulePointerManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.Function
import gnu.trove.THashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * @author nik
 */
class ModulePointerManagerImpl(private val project: Project) : ModulePointerManager() {
  private val unresolved = THashMap<String, ModulePointerImpl>()
  private val pointers = THashMap<Module, ModulePointerImpl>()
  private val lock = ReentrantReadWriteLock()

  init {
    project.messageBus.connect().subscribe(ProjectTopics.MODULES, object : ModuleListener {
      override fun beforeModuleRemoved(project: Project, module: Module) {
        unregisterPointer(module)
      }

      override fun moduleAdded(project: Project, module: Module) {
        moduleAppears(module)
      }

      override fun modulesRenamed(project: Project, modules: List<Module>, oldNameProvider: Function<Module, String>) {
        for (module in modules) {
          moduleAppears(module)
        }
      }
    })
  }

  private fun moduleAppears(module: Module) {
    lock.write {
      unresolved.remove(module.name)?.let {
        it.moduleAdded(module)
        registerPointer(module, it)
      }
    }
  }

  private fun registerPointer(module: Module, pointer: ModulePointerImpl) {
    pointers.put(module, pointer)
    Disposer.register(module, Disposable { unregisterPointer(module) })
  }

  private fun unregisterPointer(module: Module) {
    lock.write {
      pointers.remove(module)?.let {
        it.moduleRemoved(module)
        unresolved.put(it.moduleName, it)
      }
    }
  }

  @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
  override fun create(module: Module): ModulePointer {
    return lock.read { pointers.get(module) } ?: lock.write {
      pointers.get(module)?.let {
        return it
      }

      var pointer = unresolved.remove(module.name)
      if (pointer == null) {
        pointer = ModulePointerImpl(module, lock)
      }
      else {
        pointer.moduleAdded(module)
      }
      registerPointer(module, pointer)
      pointer!!
    }
  }

  override fun create(moduleName: String): ModulePointer {
    ModuleManager.getInstance(project).findModuleByName(moduleName)?.let {
      return create(it)
    }

    return lock.read {
      unresolved.get(moduleName) ?: lock.write {
        unresolved.get(moduleName)?.let {
          return it
        }

        // let's find in the pointers (if model not committed, see testDisposePointerFromUncommittedModifiableModel)
        pointers.keys.firstOrNull { it.name == moduleName }?.let {
          return create(it)
        }

        val pointer = ModulePointerImpl(moduleName, lock)
        unresolved.put(moduleName, pointer)
        pointer
      }
    }
  }
}
