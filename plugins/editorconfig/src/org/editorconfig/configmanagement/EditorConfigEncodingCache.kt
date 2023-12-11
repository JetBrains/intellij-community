// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.configmanagement

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter
import org.editorconfig.Utils
import org.editorconfig.Utils.configValueForKey
import org.editorconfig.plugincomponents.EditorConfigPropertiesService
import org.jdom.Attribute
import org.jdom.DataConversionException
import org.jdom.Element
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

@State(name = "editorConfigEncodings", storages = [Storage(StoragePathMacros.CACHE_FILE)])
internal class EditorConfigEncodingCache : PersistentStateComponent<Element?> {
  private val charsetMap: MutableMap<String, CharsetData> = ConcurrentHashMap()

  override fun getState(): Element {
    val root = Element("encodings")
    for (url in charsetMap.keys) {
      val charsetData = charsetMap[url] ?: continue
        val charsetStr = ConfigEncodingCharsetUtil.toString(charsetData.charset, charsetData.isUseBom)
        if (charsetStr != null) {
          val entryElement = Element(ENTRY_ELEMENT)
          val urlAttr = Attribute(URL_ATTR, url)
          val charsetAttr = Attribute(CHARSET_ATTR, charsetStr)
          entryElement.setAttribute(urlAttr)
          entryElement.setAttribute(charsetAttr)
          if (charsetData.isIgnored) {
            entryElement.setAttribute(IGNORE_ATTR, java.lang.Boolean.toString(
              charsetData.isIgnored))
          }
          root.addContent(entryElement)
        }
    }
    return root
  }

  override fun loadState(state: Element) {
    charsetMap.clear()
    for (fileElement in state.getChildren(ENTRY_ELEMENT)) {
      val urlAttr = fileElement.getAttribute(URL_ATTR)
      val charsetAttr = fileElement.getAttribute(CHARSET_ATTR)
      if (urlAttr != null && charsetAttr != null) {
        val url = urlAttr.value
        val charsetStr = charsetAttr.value
        val charset = ConfigEncodingCharsetUtil.toCharset(charsetStr)
        val useBom = ConfigEncodingCharsetUtil.UTF8_BOM_ENCODING == charsetStr
        if (charset != null) {
          val charsetData = CharsetData(charset, useBom)
          charsetMap[url] = charsetData
          val ignoreAttr = fileElement.getAttribute(IGNORE_ATTR)
          if (ignoreAttr != null) {
            try {
              charsetData.isIgnored = ignoreAttr.booleanValue
            }
            catch (e: DataConversionException) {
              // Ignore, do not set
            }
          }
        }
      }
    }
  }

  fun getUseUtf8Bom(project: Project?, virtualFile: VirtualFile): Boolean = getCharsetData(project, virtualFile, true)?.isUseBom ?: false

  fun getCharsetData(project: Project?, virtualFile: VirtualFile, withCache: Boolean): CharsetData? {
    if (project == null || !Utils.isEnabledFor (project, virtualFile)) return null
    if (withCache) {
      val cached = getCachedCharsetData(virtualFile)
      if (cached != null) return cached
    }
    return computeCharsetData(project, virtualFile)
  }

  fun computeAndCacheEncoding(project: Project, virtualFile: VirtualFile) {
    val key = getKey(virtualFile)
    val charsetData = getCharsetData(project, virtualFile, false)
    if (charsetData != null) {
      charsetMap[key] = charsetData
      virtualFile.charset = charsetData.charset
    }
  }

  fun getCachedEncoding(virtualFile: VirtualFile): Charset? {
    val charsetData = getCachedCharsetData(virtualFile)
    return if (charsetData != null && !charsetData.isIgnored) charsetData.charset else null
  }

  private fun getCachedCharsetData(virtualFile: VirtualFile): CharsetData? = charsetMap[getKey(virtualFile)]

  fun isIgnored(virtualFile: VirtualFile): Boolean =
    getCachedCharsetData(virtualFile).let { it != null && it.isIgnored }

  fun setIgnored(virtualFile: VirtualFile) {
    var charsetData = getCachedCharsetData(virtualFile)
    if (charsetData == null) {
      charsetData = CharsetData(Charset.defaultCharset(), false)
      charsetData.isIgnored = true
      charsetMap[getKey(virtualFile)] = charsetData
    }
    else {
      charsetData.isIgnored = true
    }
  }

  fun reset() {
    charsetMap.clear()
  }

  class CharsetData(val charset: Charset, val isUseBom: Boolean) {
    var isIgnored = false
  }

  class VfsListener : BulkVirtualFileListenerAdapter(object : VirtualFileListener {
    override fun fileCreated(event: VirtualFileEvent) {
      val file = event.file
      val project = ProjectLocator.getInstance().guessProjectForFile(file)
      if (project != null && Utils.isEnabledFor(project, file)) {
        getInstance().computeAndCacheEncoding(project, event.file)
      }
    }
  })

  companion object {
    private const val ENTRY_ELEMENT = "file"
    private const val URL_ATTR = "url"
    private const val CHARSET_ATTR = "charset"
    private const val IGNORE_ATTR = "ignore"

    @JvmStatic
    fun getInstance(): EditorConfigEncodingCache = service()

    private fun computeCharsetData(project: Project, virtualFile: VirtualFile): CharsetData? {
      val properties = EditorConfigPropertiesService.getInstance(project).getProperties(virtualFile)
      val charsetStr = properties.configValueForKey(ConfigEncodingCharsetUtil.charsetKey)
      if (!charsetStr.isEmpty()) {
        val charset = ConfigEncodingCharsetUtil.toCharset(charsetStr)
        val useBom = ConfigEncodingCharsetUtil.UTF8_BOM_ENCODING == charsetStr
        if (charset != null) {
          return CharsetData(charset, useBom)
        }
      }
      return null
    }

    private fun getKey(virtualFile: VirtualFile): String = virtualFile.url
  }
}