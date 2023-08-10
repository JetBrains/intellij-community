// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.MultiMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@State(name = "ModuleRenamingHistory", storages = [(Storage("modules.xml"))])
class ModulePointerManagerImpl(private val project: Project) : ModulePointerManager(), PersistentStateComponent<ModuleRenamingHistoryState> {
  private val unresolved = MultiMap<String, ModulePointerImpl>()
  private val pointers = MultiMap<Module, ModulePointerImpl>()
  private val lock = ReentrantReadWriteLock()
  private val oldToNewName = CollectionFactory.createSmallMemoryFootprintMap<String, String>()

  init {
    project.messageBus.connect().subscribe(ProjectTopics.MODULES, object : ModuleListener {
      override fun beforeModuleRemoved(project: Project, module: Module) {
        unregisterPointer(module)
      }

      override fun modulesAdded(project: Project, modules: List<Module>) {
        modulesAppears(modules)
      }

      override fun modulesRenamed(project: Project, modules: List<Module>, oldNameProvider: Function<in Module, String>) {
        modulesAppears(modules)
        val renamedOldToNew = modules.associateBy({ oldNameProvider.`fun`(it) }, { it.name })
        for (entry in oldToNewName.entries) {
          val newValue = renamedOldToNew.get(entry.value)
          if (newValue != null) {
            entry.setValue(newValue)
          }
        }
      }
    })
  }

  override fun getState(): ModuleRenamingHistoryState = ModuleRenamingHistoryState().apply {
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

  private fun modulesAppears(modules: List<Module>) {
    lock.write {
      for (module in modules) {
        unresolved.remove(module.name)?.forEach {
          it.moduleAdded(module)
          registerPointer(module, it)
        }
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

  override fun create(module: Module): ModulePointer {
    return lock.read { pointers.get(module).firstOrNull() } ?: lock.write {
      pointers.get(module).firstOrNull()?.let {
        return it
      }

      val pointers = unresolved.remove(module.name)
      if (pointers.isNullOrEmpty()) {
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
