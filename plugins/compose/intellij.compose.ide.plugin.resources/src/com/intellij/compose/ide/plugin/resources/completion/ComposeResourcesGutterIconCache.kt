/*
 * Copyright (C) 2013 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.resources.completion

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.UIUtil
import javax.swing.Icon
import kotlin.properties.Delegates.observable

/**
 * Project-level cache for rendered Compose Resource drawable icons
 * Based on Android's [GutterIconCache]
 * Difference: using CollectionFactory.createConcurrentSoftValueMap instead of ConcurrentHashMap
 */
@Service(Service.Level.PROJECT)
internal class ComposeResourcesGutterIconCache(private val highDpiSupplier: () -> Boolean = UIUtil::isRetina) {

  private val thumbnailCache: MutableMap<String, TimestampedIcon> = CollectionFactory.createConcurrentSoftValueMap()
  private var highDpiDisplay by observable(false) { _, oldValue, newValue -> if (oldValue != newValue) thumbnailCache.clear() }

  /** Renders the icon using [renderIcon], caches, and returns it */
  fun getIcon(file: VirtualFile, renderIcon: (VirtualFile) -> Icon?): Icon? =
    (getTimestampedIconFromCache(file) ?: renderAndCacheIcon(file, renderIcon)).icon

  /** Returns the cached [Icon] for the given [file] or null */
  fun getIconIfCached(file: VirtualFile): Icon? = getTimestampedIconFromCache(file)?.icon

  private fun renderAndCacheIcon(file: VirtualFile, renderIcon: (VirtualFile) -> Icon?): TimestampedIcon =
    TimestampedIcon(renderIcon(file), file.modificationStamp).also {
      thumbnailCache[file.path] = it
    }

  private fun getTimestampedIconFromCache(file: VirtualFile): TimestampedIcon? {
    highDpiDisplay = highDpiSupplier()
    return thumbnailCache[file.path]?.takeIf { it.isAsNewAs(file) }
  }

  private data class TimestampedIcon(val icon: Icon?, val timestamp: Long) {
    fun isAsNewAs(file: VirtualFile): Boolean =
      timestamp == file.modificationStamp && !FileDocumentManager.getInstance().isFileModified(file)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ComposeResourcesGutterIconCache = project.service()
  }
}
