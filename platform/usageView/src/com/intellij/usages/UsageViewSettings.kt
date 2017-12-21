/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages

import com.intellij.openapi.components.*
import com.intellij.util.PathUtil
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient

/**
 * Passed params will be used as default values, so, do not use constructor if instance will be used as a state (unless you want to change defaults)
 */
@State(name = "UsageViewSettings", storages = arrayOf(Storage("usageView.xml"), Storage(value = "other.xml", deprecated = true)))
open class UsageViewSettings(
  isGroupByFileStructure: Boolean = true,
  isGroupByModule: Boolean = true,
  isGroupByPackage: Boolean = true,
  isGroupByUsageType: Boolean = true,
  isGroupByScope: Boolean = false
) : BaseState(), PersistentStateComponent<UsageViewSettings> {
  companion object {
    @JvmStatic
    val instance: UsageViewSettings
      get() = ServiceManager.getService(UsageViewSettings::class.java)
  }

  @Suppress("unused")
  @JvmField
  @Transient
  @Deprecated(message = "Use isGroupByModule")
  var GROUP_BY_MODULE = isGroupByModule

  @Suppress("unused")
  @JvmField
  @Transient
  @Deprecated(message = "Use isGroupByUsageType")
  var GROUP_BY_USAGE_TYPE = isGroupByUsageType

  @Suppress("unused")
  @JvmField
  @Transient
  @Deprecated(message = "Use isGroupByFileStructure")
  var GROUP_BY_FILE_STRUCTURE = isGroupByFileStructure

  @Suppress("unused")
  @JvmField
  @Transient
  @Deprecated(message = "Use isGroupByScope")
  var GROUP_BY_SCOPE = isGroupByScope

  @Suppress("unused")
  @JvmField
  @Transient
  @Deprecated(message = "Use isGroupByPackage")
  var GROUP_BY_PACKAGE = isGroupByPackage

  @Suppress("MemberVisibilityCanPrivate")
  @get:OptionTag("EXPORT_FILE_NAME")
  internal var EXPORT_FILE_NAME by property("report.txt")

  @get:OptionTag("IS_EXPANDED")
  var isExpanded by property(false)

  @get:OptionTag("IS_AUTOSCROLL_TO_SOURCE")
  var isAutoScrollToSource by property(false)

  @get:OptionTag("IS_FILTER_DUPLICATED_LINE")
  var isFilterDuplicatedLine by property(true)

  @get:OptionTag("IS_SHOW_METHODS")
  var isShowModules by property(false)

  @get:OptionTag("IS_PREVIEW_USAGES")
  var isPreviewUsages by property(false)

  @get:OptionTag("IS_SORT_MEMBERS_ALPHABETICALLY")
  var isSortAlphabetically by property(false)

  @get:OptionTag("PREVIEW_USAGES_SPLITTER_PROPORTIONS")
  var previewUsagesSplitterProportion by property(0.5f)

  @get:OptionTag("GROUP_BY_USAGE_TYPE")
  var isGroupByUsageType by property(isGroupByUsageType)

  @get:OptionTag("GROUP_BY_MODULE")
  var isGroupByModule by property(isGroupByModule)

  @get:OptionTag("FLATTEN_MODULES")
  var isFlattenModules by property(true)

  @get:OptionTag("GROUP_BY_PACKAGE")
  var isGroupByPackage by property(isGroupByPackage)

  @get:OptionTag("GROUP_BY_FILE_STRUCTURE")
  var isGroupByFileStructure by property(isGroupByFileStructure)

  @get:OptionTag("GROUP_BY_SCOPE")
  var isGroupByScope: Boolean by property(isGroupByScope)

  var exportFileName: String?
    @Transient
    get() = PathUtil.toSystemDependentName(EXPORT_FILE_NAME)
    set(value) {
      EXPORT_FILE_NAME = PathUtil.toSystemIndependentName(value)
    }

  override fun getState() = this

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
