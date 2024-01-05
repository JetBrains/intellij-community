// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl.pluginsAdvertisement

import com.intellij.ide.BrowserUtil
import com.intellij.internal.statistic.StructuredIdeActivity
import com.intellij.internal.statistic.collectors.fus.PluginIdRuleValidator
import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.openapi.application.IdeUrlTrackingParametersProvider
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import java.util.Locale.ROOT

class PluginAdvertiserUsageCollector : CounterUsagesCollector() {
  override fun getGroup(): EventLogGroup = GROUP
}

private const val FUS_GROUP_ID = "plugins.advertiser"

private val GROUP = EventLogGroup(FUS_GROUP_ID, 11)

private val SOURCE_FIELD = EventFields.Enum(
  "source",
  FUSEventSource::class.java,
) { it.name.lowercase(ROOT) }

private val CONFIGURE_PLUGINS_EVENT = GROUP.registerEvent(
  "configure.plugins",
  SOURCE_FIELD,
)

private val PLUGINS_FIELD = EventFields.StringListValidatedByCustomRule(
  "plugins",
  PluginIdRuleValidator::class.java,
)

private val PLUGIN_FIELD = EventFields.StringValidatedByCustomRule(
  "pluginId",  // "plugin" is reserved platform key in FeatureUsageData.platformDataKeys
  PluginIdRuleValidator::class.java,
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
  PLUGIN_FIELD
)

private val TRY_ULTIMATE_EVENT = GROUP.registerEvent("try.ultimate.initiated", SOURCE_FIELD, PLUGIN_FIELD)
private val TRY_ULTIMATE_TOOLBOX_EVENT = GROUP.registerEvent("try.ultimate.toolbox.used", SOURCE_FIELD, PLUGIN_FIELD)
private val TRY_ULTIMATE_DOWNLOAD_ACTIVITY = GROUP.registerIdeActivity("try.ultimate.download", arrayOf(SOURCE_FIELD, PLUGIN_FIELD))
private val TRY_ULTIMATE_INSTALLATION_ACTIVITY = GROUP.registerIdeActivity("try.ultimate.installation", arrayOf(SOURCE_FIELD, PLUGIN_FIELD))
private val TRY_ULTIMATE_OPEN_ACTIVITY = GROUP.registerIdeActivity("try.ultimate.open", arrayOf(SOURCE_FIELD, PLUGIN_FIELD))
private val TRY_ULTIMATE_FALLBACK_EVENT = GROUP.registerEvent("try.ultimate.fallback.used", SOURCE_FIELD, PLUGIN_FIELD)
private val TRY_ULTIMATE_CANCELLED_EVENT = GROUP.registerEvent("try.ultimate.cancelled", SOURCE_FIELD, PLUGIN_FIELD)

private val LEARN_MORE_EVENT = GROUP.registerEvent(
  "learn.more",
  SOURCE_FIELD,
  PLUGIN_FIELD
)

private val IGNORE_EXTENSIONS_EVENT = GROUP.registerEvent(
  "ignore.extensions",
  SOURCE_FIELD,
)

private val IGNORE_UNKNOWN_FEATURES_EVENT = GROUP.registerEvent(
  "ignore.unknown.features",
  SOURCE_FIELD,
)

private val SUGGESTED_PLUGIN_EVENT = GROUP.registerEvent(
  "suggestion.shown",
  SOURCE_FIELD,
  PLUGIN_FIELD
)

enum class FUSEventSource {
  EDITOR,
  NOTIFICATION,
  PLUGINS_SEARCH,
  PLUGINS_SUGGESTED_GROUP,
  ACTIONS,
  SETTINGS,
  NEW_PROJECT_WIZARD,
  
  @Deprecated("Use PLUGINS_SEARCH instead")
  SEARCH;

  fun doIgnoreUltimateAndLog(project: Project? = null) {
    isIgnoreIdeSuggestion = true
    IGNORE_ULTIMATE_EVENT.log(project, this)
  }

  @JvmOverloads
  fun logConfigurePlugins(project: Project? = null): Unit = CONFIGURE_PLUGINS_EVENT.log(project, this)

  @JvmOverloads
  fun logEnablePlugins(
    plugins: List<String>,
    project: Project? = null,
  ): Unit = ENABLE_PLUGINS_EVENT.log(project, plugins, this)

  @JvmOverloads
  fun logInstallPlugins(
    plugins: List<String>,
    project: Project? = null,
  ): Unit = INSTALL_PLUGINS_EVENT.log(project, plugins, this)

  @JvmOverloads
  fun openDownloadPageAndLog(project: Project? = null, url: String, pluginId: PluginId? = null) {
    BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(url))
    OPEN_DOWNLOAD_PAGE_EVENT.log(project, this, pluginId?.idString)
  }

  @JvmOverloads
  fun logTryUltimate(project: Project? = null, pluginId: PluginId? = null) {
    TRY_ULTIMATE_EVENT.log(project, this, pluginId?.idString)
  }
  
  @JvmOverloads
  fun logTryUltimateToolboxUsed(project: Project? = null, pluginId: PluginId? = null) {
    TRY_ULTIMATE_TOOLBOX_EVENT.log(project, this, pluginId?.idString)
  }

  @JvmOverloads
  fun logTryUltimateDownloadStarted(project: Project? = null, pluginId: PluginId? = null) : StructuredIdeActivity {
    return TRY_ULTIMATE_DOWNLOAD_ACTIVITY.started(project) { listOf(SOURCE_FIELD.with(this), PLUGIN_FIELD.with (pluginId?.idString)) }
  }

  @JvmOverloads
  fun logTryUltimateInstallationStarted(project: Project? = null, pluginId: PluginId? = null) : StructuredIdeActivity {
    return TRY_ULTIMATE_INSTALLATION_ACTIVITY.started(project) { listOf(SOURCE_FIELD.with(this), PLUGIN_FIELD.with(pluginId?.idString)) }
  }

  @JvmOverloads
  fun logTryUltimateIdeOpened(project: Project? = null, pluginId: PluginId? = null) : StructuredIdeActivity {
    return TRY_ULTIMATE_OPEN_ACTIVITY.started(project) { listOf(SOURCE_FIELD.with(this), PLUGIN_FIELD.with(pluginId?.idString)) }
  }

  @JvmOverloads
  fun logTryUltimateCancelled(project: Project? = null, pluginId: PluginId? = null) {
    TRY_ULTIMATE_CANCELLED_EVENT.log(project, this, pluginId?.idString)
  }

  @JvmOverloads
  fun logTryUltimateFallback(project: Project? = null, url: String, pluginId: PluginId? = null) {
    BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(url))
    TRY_ULTIMATE_FALLBACK_EVENT.log(project, this, pluginId?.idString)
  }

  @JvmOverloads
  fun learnMoreAndLog(project: Project? = null) {
    BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl("https://www.jetbrains.com/products.html#type=ide"))
    LEARN_MORE_EVENT.log(project, this, null)
  }

  @JvmOverloads
  fun learnMoreAndLog(project: Project? = null, url: String, pluginId: PluginId?) {
    BrowserUtil.browse(IdeUrlTrackingParametersProvider.getInstance().augmentUrl(url))
    LEARN_MORE_EVENT.log(project, this, pluginId?.idString)
  }

  @JvmOverloads
  fun logIgnoreExtension(project: Project? = null): Unit = IGNORE_EXTENSIONS_EVENT.log(project, this)

  @JvmOverloads
  fun logIgnoreUnknownFeatures(project: Project? = null): Unit = IGNORE_UNKNOWN_FEATURES_EVENT.log(project, this)

  @JvmOverloads
  fun logPluginSuggested(project: Project? = null, pluginId: PluginId?) {
    SUGGESTED_PLUGIN_EVENT.log(project, this, pluginId?.idString)
  }
}