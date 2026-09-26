// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components

import com.intellij.application.options.PathMacrosCollector
import com.intellij.application.options.PathMacrosImpl
import com.intellij.application.options.ReplacePathToMacroMap
import com.intellij.openapi.application.PathMacroFilter
import com.intellij.openapi.application.PathMacros
import com.intellij.openapi.components.impl.ModulePathMacroManager
import com.intellij.openapi.module.Module
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.OSAgnosticPathUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.util.PathUtilRt
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.serialization.PathMacroUtil

open class PathMacroManager private constructor(private var pathMacros: PathMacrosImpl? = null) : PathMacroSubstitutor {
  constructor(pathMacros: PathMacros?) : this(pathMacros as? PathMacrosImpl)
  private var replacePathToMacroMap: ReplacePathToMacroMap? = null
  private var pathMacrosModificationCount: Long = 0

  private fun getPathMacros(): PathMacrosImpl {
    return pathMacros ?: PathMacrosImpl.getInstanceEx().also { pathMacros = it }
  }

  val macroFilter: PathMacroFilter
    get() = createFilter()

  open val expandMacroMap: ExpandMacroToPathMap
    get() {
      val result = ExpandMacroToPathMap()
      getPathMacros().addMacroExpands(result)
      PathMacroUtil.getGlobalSystemMacros().forEach { (key, value) ->
        result.addMacroExpand(key, value)
      }
      return result
    }

  val replacePathMap: ReplacePathToMacroMap
    @Synchronized
    get() {
      val currentModificationCount = getPathMacros().modificationCount
      var map = replacePathToMacroMap
      if (map != null && currentModificationCount == pathMacrosModificationCount) {
        return map
      }

      map = computeReplacePathMap()
      replacePathToMacroMap = map
      pathMacrosModificationCount = currentModificationCount
      return map
    }

  protected open fun computeReplacePathMap(): ReplacePathToMacroMap {
    val result = ReplacePathToMacroMap()
    getPathMacros().addMacroReplacements(result)
    PathMacroUtil.getGlobalSystemMacros().forEach { (key, value) ->
      result.addMacroReplacement(value, key)
    }
    return result
  }

  @ApiStatus.Internal
  protected fun resetCachedReplacePathMap() {
    replacePathToMacroMap = null
  }

  override fun expandPath(text: String?): String? {
    return if (text == null || Strings.isEmpty(text)) text
    else expandMacroMap.substitute(text, SystemInfoRt.isFileSystemCaseSensitive)
  }

  override fun collapsePath(text: String?, recursively: Boolean): String? {
    return if (text == null || Strings.isEmpty(text)) text
    else replacePathMap.substitute(text, SystemInfoRt.isFileSystemCaseSensitive, recursively).toString()
  }

  override fun expandPaths(element: Element) {
    expandMacroMap.substitute(element, SystemInfoRt.isFileSystemCaseSensitive)
  }

  override fun collapsePaths(element: Element, recursively: Boolean) {
    collapsePaths(element, recursively, replacePathMap)
  }

  companion object {
    @JvmStatic
    fun getInstance(componentManager: ComponentManager): PathMacroManager {
      return if (componentManager is Module) {
        ModulePathMacroManager(componentManager, PathMacros.getInstance())
      }
      else {
        componentManager.getService(PathMacroManager::class.java)
      }
    }

    @JvmStatic
    fun getInstance(module: Module): PathMacroManager {
      return ModulePathMacroManager(module, PathMacros.getInstance())
    }

    private fun createFilter(): CompositePathMacroFilter {
      return PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME.computeIfAbsent(PathMacroManager::class.java) {
        CompositePathMacroFilter(PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME.extensionList)
      }
    }

    @JvmStatic
    @ApiStatus.Internal
    fun addFileHierarchyReplacements(result: ExpandMacroToPathMap, macroName: String, path: String?) {
      path?.let {
        doAddFileHierarchyReplacements(result, Strings.trimEnd(it, "/"), "\$$macroName\$")
      }
    }

    private fun doAddFileHierarchyReplacements(result: ExpandMacroToPathMap, path: String, macro: String) {
      val parentPath = PathUtilRt.getParentPath(path)
      if (parentPath.isNotEmpty()) {
        doAddFileHierarchyReplacements(result, parentPath, "$macro/..")
      }
      result.put(macro, path)
    }

    @JvmStatic
    @ApiStatus.Internal
    fun addFileHierarchyReplacements(result: ReplacePathToMacroMap, macroName: String, path: String?, stopAt: String?) {
      if (path == null) {
        return
      }
      var macro = "\$$macroName\$"
      var currentPath: String? = Strings.trimEnd(FileUtilRt.toSystemIndependentName(path), "/")
      var overwrite = true
      while (!currentPath.isNullOrEmpty() && currentPath.contains("/") && currentPath != "/") {
        result.addReplacement(currentPath, macro, overwrite)
        if (currentPath == stopAt) break
        macro += "/.."
        currentPath = OSAgnosticPathUtil.getParent(currentPath)
        overwrite = false
      }
    }

    @JvmStatic
    fun collapsePaths(element: Element, recursively: Boolean, map: ReplacePathToMacroMap) {
      map.substitute(element, SystemInfoRt.isFileSystemCaseSensitive, recursively, createFilter())
    }

    @JvmStatic
    protected fun pathsEqual(path1: String?, path2: String?): Boolean {
      return path1 != null && path2 != null && FileUtil.pathsEqual(FileUtilRt.toSystemIndependentName(path1),
                                                                   FileUtilRt.toSystemIndependentName(path2))
    }
  }
}