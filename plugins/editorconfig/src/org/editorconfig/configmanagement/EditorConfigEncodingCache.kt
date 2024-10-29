// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package org.editorconfig.configmanagement

import com.intellij.concurrency.ConcurrentCollectionFactory
import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileEvent
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.impl.BulkVirtualFileListenerAdapter
import com.intellij.platform.settings.CacheTag
import com.intellij.platform.settings.SettingsController
import com.intellij.platform.settings.settingDescriptorFactory
import com.intellij.util.containers.ConcurrentIntObjectMap
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.editorconfig.Utils
import org.editorconfig.Utils.configValueForKey
import org.editorconfig.plugincomponents.EditorConfigPropertiesService
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

@Service
internal class EditorConfigEncodingCache : SettingsSavingComponent {
  private val idCharsetMap: ConcurrentIntObjectMap<CharsetData>
  private val urlCharsetMap: ConcurrentHashMap<String, CharsetData> // only for files without Int id

  private val isChanged = AtomicBoolean()

  private val urlsSetting = settingDescriptorFactory(PluginId.getId("org.editorconfig.editorconfigjetbrains")).let { factory ->
    factory.settingDescriptor("encodingsUrls", factory.mapSerializer(String::class.java, CharsetData::class.java)) {
      tags = listOf(CacheTag)
    }
  }
  private val idsSetting = settingDescriptorFactory(PluginId.getId("org.editorconfig.editorconfigjetbrains")).let { factory ->
    factory.settingDescriptor("encodingsIds", factory.mapSerializer(Int::class.java, CharsetData::class.java)) {
      tags = listOf(CacheTag)
    }
  }

  init {
    val service = service<SettingsController>()
    urlCharsetMap = ConcurrentHashMap(service.getItem(urlsSetting) ?: emptyMap())

    val idsCharsets = service.getItem(idsSetting) ?: emptyMap()
    idCharsetMap = ConcurrentCollectionFactory.createConcurrentIntObjectMap<CharsetData>(idsCharsets.size, 0.75f, 2)
    for (entry in idsCharsets) {
      idCharsetMap.put(entry.key, entry.value)
    }
  }

  override suspend fun save() {
    if (isChanged.compareAndSet(true, false)) {
      serviceAsync<SettingsController>().setItem(urlsSetting, TreeMap(urlCharsetMap))
      val idsMap = idCharsetMap.entrySet().associate { it.key to it.value }
      serviceAsync<SettingsController>().setItem(idsSetting, TreeMap<Int, CharsetData>(idsMap))
    }
  }

  fun getUseUtf8Bom(project: Project?, virtualFile: VirtualFile): Boolean {
    return getCharsetData(project = project, virtualFile = virtualFile, withCache = true)?.isUseBom ?: false
  }

  fun getCharsetData(project: Project?, virtualFile: VirtualFile, withCache: Boolean): CharsetData? {
    if (project == null || !Utils.isEnabledFor(project, virtualFile)) {
      return null
    }

    if (withCache) {
      getCachedCharsetData(virtualFile)?.let {
        return it
      }
    }
    return computeCharsetData(project = project, virtualFile = virtualFile)
  }

  fun computeAndCacheEncoding(project: Project, virtualFile: VirtualFile) {
    val intKey = getIntKey(virtualFile)
    val key = getKey(virtualFile)
    val charsetData = getCharsetData(project = project, virtualFile = virtualFile, withCache = false)
    if (charsetData != null) {
      if (intKey > 0) {
        idCharsetMap.put(intKey, charsetData)
      }
      else {
        urlCharsetMap.put(key, charsetData)
      }

      isChanged.set(true)
      charsetData.getCharset()?.let {
        virtualFile.charset = it
      }
    }
  }

  fun getCachedEncoding(virtualFile: VirtualFile): Charset? {
    val charsetData = getCachedCharsetData(virtualFile)
    return if (charsetData == null || charsetData.isIgnored) null else charsetData.getCharset()
  }

  private fun getCachedCharsetData(virtualFile: VirtualFile): CharsetData? {
    val intKey = getIntKey(virtualFile)
    if (intKey > 0) return idCharsetMap[intKey]

    return urlCharsetMap[getKey(virtualFile)]
  }

  fun isIgnored(virtualFile: VirtualFile): Boolean = getCachedCharsetData(virtualFile).let { it != null && it.isIgnored }

  fun setIgnored(virtualFile: VirtualFile) {
    var charsetData = getCachedCharsetData(virtualFile)
    if (charsetData == null) {
      charsetData = CharsetData(charset = CharsetRef(id = null))
      charsetData.isIgnored = true

      val intKey = getIntKey(virtualFile)
      if (intKey > 0) {
        idCharsetMap.put(intKey, charsetData)
      }
      else {
        urlCharsetMap.put(getKey(virtualFile), charsetData)
      }

      isChanged.set(true)
    }
    else {
      charsetData.isIgnored = true
    }
  }

  fun reset() {
    idCharsetMap.clear()
    urlCharsetMap.clear()
    isChanged.set(true)
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
    fun getInstance(): EditorConfigEncodingCache = service()

    private fun computeCharsetData(project: Project, virtualFile: VirtualFile): CharsetData? {
      val properties = EditorConfigPropertiesService.getInstance(project).getProperties(virtualFile)
      val charsetStr = properties.configValueForKey(ConfigEncodingCharsetUtil.charsetKey).takeIf { it.isNotEmpty() } ?: return null
      val charset = ConfigEncodingCharsetUtil.toCharset(charsetStr) ?: return null
      val charsetRef = CharsetRef(charsetStr)
      charsetRef.charset = charset
      return CharsetData(charset = charsetRef)
    }

    private fun getIntKey(virtualFile: VirtualFile): Int {
      if (virtualFile is VirtualFileWithId) {
        return virtualFile.id
      }
      return -1
    }

    private fun getKey(virtualFile: VirtualFile): String = virtualFile.url
  }
}

@Serializable
internal data class CharsetRef(@JvmField val id: String?) {
  @Transient
  @JvmField
  var charset: Charset? = null
}

@Serializable
internal class CharsetData(@JvmField val charset: CharsetRef, @JvmField var isIgnored: Boolean = false) {
  val isUseBom: Boolean
    get() = charset.id == ConfigEncodingCharsetUtil.UTF8_BOM_ENCODING

  fun getCharset(): Charset? {
    var result = charset.charset
    if (result == null) {
      result = charset.id?.let { ConfigEncodingCharsetUtil.toCharset(charset.id) }
      charset.charset = result
    }
    return result
  }
}
