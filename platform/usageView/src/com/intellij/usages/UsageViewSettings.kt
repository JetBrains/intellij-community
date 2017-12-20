/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.usages

import com.intellij.openapi.components.*
import com.intellij.util.PathUtil
import com.intellij.util.xmlb.XmlSerializerUtil
import com.intellij.util.xmlb.annotations.OptionTag
import com.intellij.util.xmlb.annotations.Transient

@State(name = "UsageViewSettings", storages = arrayOf(Storage("usageView.xml"), Storage(value = "other.xml", deprecated = true)))
class UsageViewSettings : BaseState(), PersistentStateComponent<UsageViewSettings> {
  companion object {
    @JvmStatic
    val instance: UsageViewSettings
      get() = ServiceManager.getService(UsageViewSettings::class.java)
  }

  var EXPORT_FILE_NAME by string("report.txt")

  var isExpanded by property(false)
  var isShowPackages by property(true)
  var isShowMethods by property(false)

  @get:OptionTag("IS_AUTOSCROLL_TO_SOURCE")
  var isAutoScrollToSource by property(false)

  var isFilterDuplicatedLine by property(true)
  var isShowModules by property(false)

  @get:OptionTag("IS_PREVIEW_USAGES")
  var isPreviewUsages by property(false)

  @get:OptionTag("IS_SORT_MEMBERS_ALPHABETICALLY")
  var isSortAlphabetically by property(false)

  @get:OptionTag("PREVIEW_USAGES_SPLITTER_PROPORTIONS")
  var previewUsagesSplitterProportion by property(0.5f)

  @get:OptionTag("GROUP_BY_USAGE_TYPE")
  var isGroupByUsageType by property(true)

  @get:OptionTag("GROUP_BY_MODULE")
  var isGroupByModule by property(true)

  @get:OptionTag("FLATTEN_MODULES")
  var isFlattenModules by property(true)

  @get:OptionTag("GROUP_BY_PACKAGE")
  var isGroupByPackage by property(true)

  @get:OptionTag("GROUP_BY_FILE_STRUCTURE")
  var isGroupByFileStructure by property(true)

  @get:OptionTag("GROUP_BY_SCOPE")
  var isGroupByScope: Boolean by property(false)

  var exportFileName: String?
    @Transient
    get() = PathUtil.toSystemDependentName(EXPORT_FILE_NAME)
    set(value) {
      EXPORT_FILE_NAME = PathUtil.toSystemIndependentName(value)
    }

  override fun getState() = this

  override fun loadState(`object`: UsageViewSettings) {
    XmlSerializerUtil.copyBean(`object`, this)
  }
}
