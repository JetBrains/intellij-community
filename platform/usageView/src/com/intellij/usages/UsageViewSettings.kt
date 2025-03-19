// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.util.PathUtil
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient

/**
 * Passed params will be used as default values, so, do not use constructor if instance will be used as a state (unless you want to change defaults)
 */
@Suppress("PropertyName")
@State(name = "UsageViewSettings", storages = [Storage("usageView.xml")], reportStatistic = true, category = SettingsCategory.UI)
open class UsageViewSettings(
  isGroupByFileStructure: Boolean = true,
  isGroupByModule: Boolean = true,
  isGroupByPackage: Boolean = true,
  isGroupByUsageType: Boolean = true,
  isGroupByScope: Boolean = false,
  isGroupByDirectoryStructure: Boolean = false
) : BaseState(), PersistentStateComponent<UsageViewSettings> {
  companion object {
    @JvmStatic
    val instance: UsageViewSettings
      get() = ApplicationManager.getApplication().getService(UsageViewSettings::class.java)
  }

  @JvmField
  @Transient
  @Deprecated(message = "Use isGroupByModule")
  var GROUP_BY_MODULE: Boolean = isGroupByModule

  @JvmField
  @Transient
  @Deprecated(message = "Use isGroupByUsageType")
  var GROUP_BY_USAGE_TYPE: Boolean = isGroupByUsageType

  @JvmField
  @Transient
  @Deprecated(message = "Use isGroupByFileStructure")
  var GROUP_BY_FILE_STRUCTURE: Boolean = isGroupByFileStructure

  @JvmField
  @Transient
  @Deprecated(message = "Use isGroupByScope")
  var GROUP_BY_SCOPE: Boolean = isGroupByScope

  @JvmField
  @Transient
  @Deprecated(message = "Use isGroupByPackage")
  var GROUP_BY_PACKAGE: Boolean = isGroupByPackage

  @Suppress("MemberVisibilityCanPrivate")
  @get:OptionTag("EXPORT_FILE_NAME")
  internal var EXPORT_FILE_NAME: String? by string("report.txt")

  @get:OptionTag("IS_EXPANDED")
  var isExpanded: Boolean by property(false)

  @get:OptionTag("IS_AUTOSCROLL_TO_SOURCE")
  var isAutoScrollToSource: Boolean by property(false)

  @get:OptionTag("IS_FILTER_DUPLICATED_LINE")
  var isFilterDuplicatedLine: Boolean by property(true)

  @get:OptionTag("IS_SHOW_METHODS")
  var isShowModules: Boolean by property(false)

  @get:OptionTag("IS_PREVIEW_USAGES")
  var isPreviewUsages: Boolean by property(true)

  @get:OptionTag("IS_REPLACE_PREVIEW_USAGES")
  var isReplacePreviewUsages: Boolean by property(true)

  @get:OptionTag("IS_SORT_MEMBERS_ALPHABETICALLY")
  var isSortAlphabetically: Boolean by property(false)

  @get:OptionTag("PREVIEW_USAGES_SPLITTER_PROPORTIONS")
  var previewUsagesSplitterProportion: Float by property(0.5f)

  @get:OptionTag("GROUP_BY_USAGE_TYPE")
  open var isGroupByUsageType: Boolean by property(isGroupByUsageType)

  @get:OptionTag("GROUP_BY_MODULE")
  open var isGroupByModule: Boolean by property(isGroupByModule)

  @get:OptionTag("FLATTEN_MODULES")
  var isFlattenModules: Boolean by property(true)

  @get:OptionTag("GROUP_BY_PACKAGE")
  open var isGroupByPackage: Boolean by property(isGroupByPackage)

  @get:OptionTag("GROUP_BY_FILE_STRUCTURE")
  var isGroupByFileStructure: Boolean by property(isGroupByFileStructure)

  @get:OptionTag("GROUP_BY_DIRECTORY_STRUCTURE")
  open var isGroupByDirectoryStructure: Boolean by property(isGroupByDirectoryStructure)

  /**
   * Compact middle directories option for directories usage grouping
   */
  @get:OptionTag("COMPACT_MIDDLE_DIRECTORIES")
  var isCompactMiddleDirectories: Boolean by property(false)

  @get:OptionTag("GROUP_BY_SCOPE")
  open var isGroupByScope: Boolean by property(isGroupByScope)

  var exportFileName: String?
    @Transient
    get() = PathUtil.toSystemDependentName(EXPORT_FILE_NAME)
    set(value) {
      EXPORT_FILE_NAME = PathUtil.toSystemIndependentName(value)
    }

  override fun getState(): UsageViewSettings = this

  @Suppress("DEPRECATION")
  override fun loadState(state: UsageViewSettings) {
    copyFrom(state)
    GROUP_BY_MODULE = isGroupByModule
    GROUP_BY_USAGE_TYPE = isGroupByUsageType
    GROUP_BY_FILE_STRUCTURE = isGroupByFileStructure
    GROUP_BY_SCOPE = isGroupByScope
    GROUP_BY_PACKAGE = isGroupByPackage
  }
}
