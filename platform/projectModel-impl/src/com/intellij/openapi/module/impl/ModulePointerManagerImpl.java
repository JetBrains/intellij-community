/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

/**
 * @author nik
 */
class ModulePointerManagerImpl(private val myProject: Project) : ModulePointerManager() {
  private val myUnresolved = THashMap<String, ModulePointerImpl>()
  private val myPointers = THashMap<Module, ModulePointerImpl>()

  init {
    myProject.messageBus.connect().subscribe(ProjectTopics.MODULES, object : ModuleListener {
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

  @Synchronized private fun moduleAppears(module: Module) {
    val pointer = myUnresolved.remove(module.name)
    if (pointer != null && pointer.moduleAdded(module)) {
      registerPointer(module, pointer)
    }
  }

  private fun registerPointer(module: Module, pointer: ModulePointerImpl) {
    myPointers.put(module, pointer)
    Disposer.register(module, Disposable { unregisterPointer(module) })
  }

  @Synchronized private fun unregisterPointer(module: Module) {
    val pointer = myPointers.remove(module)
    if (pointer != null) {
      pointer.moduleRemoved(module)
      myUnresolved.put(pointer.moduleName, pointer)
    }
  }

  @Synchronized override fun create(module: Module): ModulePointer {
    var pointer: ModulePointerImpl? = myPointers[module]
    if (pointer == null) {
      pointer = myUnresolved[module.name]
      if (pointer == null) {
        pointer = ModulePointerImpl(module)
      }
      else {
        pointer.moduleAdded(module)
      }
      registerPointer(module, pointer)
    }
    return pointer
  }

  @Synchronized override fun create(moduleName: String): ModulePointer {
    val module = ModuleManager.getInstance(myProject).findModuleByName(moduleName)
    if (module != null) {
      return create(module)
    }

    var pointer: ModulePointerImpl? = myUnresolved[moduleName]
    if (pointer == null) {
      pointer = ModulePointerImpl(moduleName)
      myUnresolved.put(moduleName, pointer)
    }
    return pointer
  }
}
