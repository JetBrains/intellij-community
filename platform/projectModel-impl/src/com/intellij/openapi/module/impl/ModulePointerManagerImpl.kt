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
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.module.ModulePointer
import com.intellij.openapi.module.ModulePointerManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.util.Function
import com.intellij.util.containers.MultiMap
import gnu.trove.THashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * @author nik
 */
@State(name = "ModuleRenamingHistory", storages = arrayOf(Storage("modules.xml")))
class ModulePointerManagerImpl(private val project: Project) : ModulePointerManager(), PersistentStateComponent<ModuleRenamingHistoryState> {
  private val unresolved = MultiMap.createSmart<String, ModulePointerImpl>()
  private val pointers = MultiMap.createSmart<Module, ModulePointerImpl>()
  private val lock = ReentrantReadWriteLock()
  private val oldToNewName = THashMap<String, String>()

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

  override fun getState() = ModuleRenamingHistoryState().apply {
    lock.read {
      oldToNewName.putAll(this@ModulePointerManagerImpl.oldToNewName)
    }
  }

  override fun loadState(state: ModuleRenamingHistoryState) {
    setRenamingScheme(state.oldToNewName)
  }

  fun setRenamingScheme(renamingScheme: Map<String, String>) {
    lock.write {
      oldToNewName.clear()
      oldToNewName.putAll(renamingScheme)

      val moduleManager = ModuleManager.getInstance(project)
      renamingScheme.entries.forEach { (oldName, newName) ->
        val oldModule = moduleManager.findModuleByName(oldName)
        if (oldModule != null) {
          unregisterPointer(oldModule)
        }
        updateUnresolvedPointers(oldName, newName, moduleManager)
      }
    }
  }

  private fun updateUnresolvedPointers(oldName: String,
                                       newName: String,
                                       moduleManager: ModuleManager) {
    val pointers = unresolved.remove(oldName)
    pointers?.forEach { pointer ->
      pointer.renameUnresolved(newName)
      val module = moduleManager.findModuleByName(newName)
      if (module != null) {
        pointer.moduleAdded(module)
        registerPointer(module, pointer)
      }
      else {
        unresolved.putValue(newName, pointer)
      }
    }
  }

  private fun moduleAppears(module: Module) {
    lock.write {
      unresolved.remove(module.name)?.forEach {
        it.moduleAdded(module)
        registerPointer(module, it)
      }
    }
  }

  private fun registerPointer(module: Module, pointer: ModulePointerImpl) {
    pointers.putValue(module, pointer)
    Disposer.register(module, Disposable { unregisterPointer(module) })
  }

  private fun unregisterPointer(module: Module) {
    lock.write {
      pointers.remove(module)?.forEach {
        it.moduleRemoved(module)
        unresolved.putValue(it.moduleName, it)
      }
    }
  }

  @Suppress("UNNECESSARY_NOT_NULL_ASSERTION")
  override fun create(module: Module): ModulePointer {
    return lock.read { pointers.get(module).firstOrNull() } ?: lock.write {
      pointers.get(module).firstOrNull()?.let {
        return it
      }

      val pointers = unresolved.remove(module.name)
      if (pointers == null || pointers.isEmpty()) {
        val pointer = ModulePointerImpl(module, lock)
        registerPointer(module, pointer)
        pointer
      }
      else {
        pointers.forEach {
          it.moduleAdded(module)
          registerPointer(module, it)
        }
        pointers.first()
      }
    }
  }

  override fun create(moduleName: String): ModulePointer {
    ModuleManager.getInstance(project).findModuleByName(moduleName)?.let {
      return create(it)
    }

    val newName = lock.read { oldToNewName[moduleName] }
    if (newName != null) {
      ModuleManager.getInstance(project).findModuleByName(newName)?.let {
        return create(it)
      }
    }

    return lock.read {
      unresolved.get(moduleName).firstOrNull() ?: lock.write {
        unresolved.get(moduleName).firstOrNull()?.let {
          return it
        }

        // let's find in the pointers (if model not committed, see testDisposePointerFromUncommittedModifiableModel)
        pointers.keySet().firstOrNull { it.name == moduleName }?.let {
          return create(it)
        }

        val pointer = ModulePointerImpl(moduleName, lock)
        unresolved.putValue(moduleName, pointer)
        pointer
      }
    }
  }
}
