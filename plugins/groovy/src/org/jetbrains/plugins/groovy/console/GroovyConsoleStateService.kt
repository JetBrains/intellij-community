// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.console

import com.intellij.openapi.components.*
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import org.jetbrains.plugins.groovy.console.GroovyConsoleStateService.MyState

import java.util.*

@State(name = "GroovyConsoleState", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class GroovyConsoleStateService(
  private val moduleManager: ModuleManager,
  private val fileManager: VirtualFileManager
) : PersistentStateComponent<MyState> {

  private val myFileModuleMap: MutableMap<VirtualFile, Pair<Module?, String?>> = Collections.synchronizedMap(HashMap())

  class Entry {
    var url: String? = null
    var moduleName: String? = null
    var title: String? = null
  }

  class MyState {
    var list: MutableCollection<Entry> = ArrayList()
  }

  override fun getState(): MyState {
    synchronized(myFileModuleMap) {
      val result = MyState()
      for ((file, pair) in myFileModuleMap) {
        val e = Entry()
        e.url = file.url
        e.moduleName = pair.first?.name ?: ""
        e.title = pair.second
        result.list.add(e)
      }
      return result
    }
  }

  override fun loadState(state: MyState) {
    synchronized(myFileModuleMap) {
      myFileModuleMap.clear()
      for (entry in state.list) {
        val url = entry.url ?: continue
        val file = fileManager.findFileByUrl(url) ?: continue
        val moduleName = entry.moduleName
        val module = if (moduleName == null) null else moduleManager.findModuleByName(moduleName)
        myFileModuleMap[file] = Pair.create(module, entry.title)
      }
    }
  }

  fun isProjectConsole(file: VirtualFile): Boolean {
    return myFileModuleMap.containsKey(file)
  }

  fun getSelectedModule(file: VirtualFile): Module? = myFileModuleMap[file]?.first

  fun getSelectedModuleTitle(file: VirtualFile): String? = myFileModuleMap[file]?.second

  fun setFileModule(file: VirtualFile, module: Module) {
    myFileModuleMap[file] = Pair.create(module, GroovyConsoleUtil.getTitle(module))
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): GroovyConsoleStateService = project.service()
  }
}
