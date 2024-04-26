// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console

import com.intellij.openapi.components.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModulePointer
import com.intellij.openapi.module.ModulePointerManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.plugins.groovy.console.GroovyConsoleStateService.MyState
import java.util.*

@Service(Service.Level.PROJECT)
@State(name = "GroovyConsoleState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class GroovyConsoleStateService(private val project: Project) : PersistentStateComponent<MyState> {
  private val fileModuleMap: MutableMap<VirtualFile, ModulePointer?> = Collections.synchronizedMap(HashMap())

  class Entry {
    var url: String? = null
    var moduleName: String? = null
  }

  class MyState {
    var list: MutableCollection<Entry> = ArrayList()
  }

  override fun getState(): MyState {
    synchronized(fileModuleMap) {
      val result = MyState()
      for ((file, pointer) in fileModuleMap) {
        val e = Entry()
        e.url = file.url
        e.moduleName = pointer?.moduleName
        result.list.add(e)
      }
      return result
    }
  }

  override fun loadState(state: MyState) {
    val virtualFileManager = VirtualFileManager.getInstance()
    val modulePointerManager = ModulePointerManager.getInstance(project)
    synchronized(fileModuleMap) {
      fileModuleMap.clear()
      for (entry in state.list) {
        val url = entry.url ?: continue
        val file = virtualFileManager.findFileByUrl(url) ?: continue
        val pointer = entry.moduleName?.let(modulePointerManager::create)
        fileModuleMap[file] = pointer
      }
    }
  }

  fun isProjectConsole(file: VirtualFile): Boolean {
    return fileModuleMap.containsKey(file)
  }

  fun getSelectedModule(file: VirtualFile): Module? = fileModuleMap[file]?.module

  fun setFileModule(file: VirtualFile, module: Module?) {
    fileModuleMap[file] = module?.let(ModulePointerManager.getInstance(project)::create)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GroovyConsoleStateService = project.service()
  }
}
