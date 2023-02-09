// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.editorconfig.core.ec4jwrappers

import com.intellij.openapi.util.SimpleModificationTracker
import org.ec4j.core.Cache
import org.ec4j.core.EditorConfigLoader
import org.ec4j.core.Resource
import org.ec4j.core.model.EditorConfig
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

// TODO make this a part of settings provider component
class EditorConfigPermanentCache : Cache, SimpleModificationTracker() {
  private val entries: ConcurrentMap<Resource, EditorConfig> = ConcurrentHashMap()
  private var accessRecord: MutableList<Resource>? = null

  // TODO what should happen if an editorconfig file is missing? the file might even get deleted meanwhile ..
  @Synchronized
  override fun get(editorConfigFile: Resource, loader: EditorConfigLoader): EditorConfig {
    accessRecord?.add(editorConfigFile)
    return entries.getOrPut(editorConfigFile) {
      // TODO this might be called even if the key already exists -- not perfect
      // TODO it might throw. what happens then?
      val res = loader.load(editorConfigFile)
      // Only if loading didn't throw mark me as modified
      incModificationCount()
      res
    }
  }

  @Synchronized
  fun doWhileRecordingAccess(block: EditorConfigPermanentCache.() -> Unit): List<Resource> {
    startRecordingAccess()
    lateinit var recorded: List<Resource>
    try {
      this.block()
    } finally {
      recorded = endRecordingAccess()
    }
    return recorded
  }

  private fun startRecordingAccess() {
    require(accessRecord == null)
    accessRecord = mutableListOf()
  }

  private fun endRecordingAccess(): List<Resource> {
    require(accessRecord != null)
    return accessRecord!!.apply { accessRecord = null }
  }

  fun clear() = entries.clear()
}