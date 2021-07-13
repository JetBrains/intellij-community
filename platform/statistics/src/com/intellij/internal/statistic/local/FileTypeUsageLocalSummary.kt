// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.local

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.xmlb.annotations.Attribute
import com.intellij.util.xmlb.annotations.Property
import com.intellij.util.xmlb.annotations.Tag
import com.intellij.util.xmlb.annotations.XMap

@State(name = "FileTypeUsageLocalSummary",
       storages = [Storage("fileTypeUsageSummary.xml", roamingType = RoamingType.DISABLED)],
       reportStatistic = false)
@Service(Service.Level.PROJECT)
class FileTypeUsageLocalSummary : PersistentStateComponent<FileTypeUsageLocalSummaryState>, SimpleModificationTracker() {
  @Volatile
  private var state = FileTypeUsageLocalSummaryState()

  override fun getState() = state

  override fun loadState(state: FileTypeUsageLocalSummaryState) {
    this.state = state
  }

  fun getFileTypeStats(): Map<String, FileTypeUsageSummary> = if (state.data.isEmpty()) emptyMap() else HashMap(state.data)

  fun getFileTypeStatsByName(fileTypeName: String): FileTypeUsageSummary? = state.data[fileTypeName]

  @Synchronized
  internal fun updateFileTypeSummary(fileTypeName: String) {
    val summary = state.data.computeIfAbsent(fileTypeName) { FileTypeUsageSummary() }
    summary.usageCount++
    summary.lastUsed = System.currentTimeMillis()

    incModificationCount()
  }
}

@Tag("summary")
class FileTypeUsageSummary {
  @Attribute("usageCount")
  @JvmField
  var usageCount = 0

  @Attribute("lastUsed")
  @JvmField
  var lastUsed = System.currentTimeMillis()

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as FileTypeUsageSummary
    return usageCount == other.usageCount && lastUsed == other.lastUsed
  }

  override fun hashCode() = (31 * usageCount) + lastUsed.hashCode()
}

data class FileTypeUsageLocalSummaryState(
  @get:XMap(entryTagName = "fileType", keyAttributeName = "name")
  @get:Property(surroundWithTag = false)
  internal val data: MutableMap<String, FileTypeUsageSummary> = HashMap()
)

private class FileTypeSummaryListener : FileEditorManagerListener {
  private val service = ApplicationManager.getApplication().getService(FileTypeUsageLocalSummary::class.java)
                        ?: throw ExtensionNotApplicableException.INSTANCE

  override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
    val fileTypeName = file.fileType.name
    service.updateFileTypeSummary(fileTypeName)
  }
}
