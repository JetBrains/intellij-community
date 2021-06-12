// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.BrowserUtil
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider
import com.intellij.openapi.project.Project
import java.util.Locale.ROOT

private const val FUS_GROUP_ID = "plugins.advertiser"

private val GROUP = EventLogGroup(
  FUS_GROUP_ID,
  1,
)

private val SOURCE_FIELD = EventFields.Enum(
  "source",
  FUSEventSource::class.java,
) { it.name.toLowerCase(ROOT) }

private val CONFIGURE_PLUGINS_EVENT = GROUP.registerEvent(
  "configure.plugins",
  SOURCE_FIELD,
)

private val PLUGINS_FIELD = EventFields.StringListValidatedByCustomRule(
  "plugins",
  "plugin",
)

private val ENABLE_PLUGINS_EVENT = GROUP.registerEvent(
  "enable.plugins",
  PLUGINS_FIELD,
  SOURCE_FIELD,
)

private val INSTALL_PLUGINS_EVENT = GROUP.registerEvent(
  "install.plugins",
  PLUGINS_FIELD,
  SOURCE_FIELD,
)

private val IGNORE_ULTIMATE_EVENT = GROUP.registerEvent(
  "ignore.ultimate",
  SOURCE_FIELD,
)

private val OPEN_DOWNLOAD_PAGE_EVENT = GROUP.registerEvent(
  "open.download.page",
  SOURCE_FIELD,
)

private val IGNORE_EXTENSIONS_EVENT = GROUP.registerEvent(
  "ignore.extensions",
  SOURCE_FIELD,
)

private val IGNORE_UNKNOWN_FEATURES_EVENT = GROUP.registerEvent(
  "ignore.unknown.features",
  SOURCE_FIELD,
)

enum class FUSEventSource {
  EDITOR,
  NOTIFICATION;

  fun doIgnoreUltimateAndLog(project: Project? = null) {
    isIgnoreUltimate = true
    IGNORE_ULTIMATE_EVENT.log(project, this)
  }

  @JvmOverloads
  fun logConfigurePlugins(project: Project? = null) = CONFIGURE_PLUGINS_EVENT.log(project, this)

  @JvmOverloads
  fun logEnablePlugins(
    plugins: List<String>,
    project: Project? = null,
  ) = ENABLE_PLUGINS_EVENT.log(project, plugins, this)

  @JvmOverloads
  fun logInstallPlugins(
    plugins: List<String>,
    project: Project? = null,
  ) = INSTALL_PLUGINS_EVENT.log(project, plugins, this)

  @JvmOverloads
  fun openDownloadPageAndLog(project: Project? = null) {
    BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl("https://www.jetbrains.com/idea/download/"))
    OPEN_DOWNLOAD_PAGE_EVENT.log(project, this)
  }

  @JvmOverloads
  fun logIgnoreExtension(project: Project? = null) = IGNORE_EXTENSIONS_EVENT.log(project, this)

  @JvmOverloads
  fun logIgnoreUnknownFeatures(project: Project? = null) = IGNORE_UNKNOWN_FEATURES_EVENT.log(project, this)
}
