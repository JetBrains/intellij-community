// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.statistics

import com.intellij.internal.statistic.beans.MetricEvent
import com.intellij.internal.statistic.beans.addBoolIfDiffers
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.ApplicationUsagesCollector
import com.intellij.openapi.vcs.VcsApplicationSettings

internal class VcsApplicationOptionsUsagesCollector : ApplicationUsagesCollector() {
  private val GROUP = EventLogGroup("vcs.application.configuration", 5)
  private val SHOW_WHITESPACES_IN_LST = GROUP.registerVarargEvent("show.whitespaces.in.lst", EventFields.Enabled)
  private val SHOW_LST_GUTTER_MARKERS = GROUP.registerVarargEvent("show.lst.gutter.markers", EventFields.Enabled)
  private val SHOW_LST_ERROR_STRIPE_MARKERS = GROUP.registerVarargEvent("show.lst.error.stripe.markers", EventFields.Enabled)
  private val DETECT_PATCH_ON_THE_FLY = GROUP.registerVarargEvent("detect.patch.on.the.fly", EventFields.Enabled)
  private val CREATE_CHANGELISTS_AUTOMATICALLY = GROUP.registerVarargEvent("create.changelists.automatically", EventFields.Enabled)
  private val ENABLE_PARTIAL_CHANGELISTS = GROUP.registerVarargEvent("enable.partial.changelists", EventFields.Enabled)
  private val MANAGE_IGNORE_FILES = GROUP.registerVarargEvent("manage.ignore.files", EventFields.Enabled)
  private val DISABLE_MANAGE_IGNORE_FILES = GROUP.registerVarargEvent("disable.manage.ignore.files", EventFields.Enabled)
  private val MARK_EXCLUDED_AS_IGNORED = GROUP.registerVarargEvent("mark.excluded.as.ignored", EventFields.Enabled)
  private val NON_MODAL_COMMIT = GROUP.registerVarargEvent("non.modal.commit", EventFields.Enabled)
  private val SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK = GROUP.registerVarargEvent("show.editor.preview.on.double.click", EventFields.Enabled)
  private val SHOW_DIFF_ON_DOUBLE_CLICK = GROUP.registerVarargEvent("show.diff.on.double.click", EventFields.Enabled)

  override fun getGroup(): EventLogGroup {
    return GROUP
  }

  override fun getMetrics(): Set<MetricEvent> {
    val defaultSettings = VcsApplicationSettings()
    val appSettings = VcsApplicationSettings.getInstance()

    return mutableSetOf<MetricEvent>().apply {
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.SHOW_WHITESPACES_IN_LST }, SHOW_WHITESPACES_IN_LST)
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.SHOW_LST_GUTTER_MARKERS }, SHOW_LST_GUTTER_MARKERS)
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.SHOW_LST_ERROR_STRIPE_MARKERS }, SHOW_LST_ERROR_STRIPE_MARKERS)
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.DETECT_PATCH_ON_THE_FLY }, DETECT_PATCH_ON_THE_FLY)
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.CREATE_CHANGELISTS_AUTOMATICALLY }, CREATE_CHANGELISTS_AUTOMATICALLY)
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.ENABLE_PARTIAL_CHANGELISTS }, ENABLE_PARTIAL_CHANGELISTS)
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.MANAGE_IGNORE_FILES }, MANAGE_IGNORE_FILES)
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.DISABLE_MANAGE_IGNORE_FILES }, DISABLE_MANAGE_IGNORE_FILES)
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.MARK_EXCLUDED_AS_IGNORED }, MARK_EXCLUDED_AS_IGNORED)
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.COMMIT_FROM_LOCAL_CHANGES }, NON_MODAL_COMMIT)
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK }, SHOW_EDITOR_PREVIEW_ON_DOUBLE_CLICK)
      addBoolIfDiffers(this, appSettings, defaultSettings, { it.SHOW_DIFF_ON_DOUBLE_CLICK }, SHOW_DIFF_ON_DOUBLE_CLICK)
    }
  }
}