// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.application.impl

import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.BuildNumber
import java.util.Calendar

@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
internal class ApplicationInfoServiceDelegate : ApplicationInfoEx() {
  private val delegate = ApplicationInfoImpl.getShadowInstance()

  override fun getBuildDate(): Calendar? = delegate.buildDate
  override fun getBuildTime() = delegate.buildTime

  override fun getBuild(): BuildNumber = delegate.build
  override fun getBuildBranchName(): String? = delegate.buildBranchName
  override fun getApiVersion(): String = delegate.apiVersion
  override fun getMajorVersion(): String? = delegate.majorVersion
  override fun getMinorVersion(): String? = delegate.minorVersion
  override fun getMicroVersion(): String? = delegate.microVersion
  override fun getPatchVersion(): String? = delegate.patchVersion
  override fun getVersionName(): String? = delegate.versionName
  override fun getCompanyName(): String? = delegate.companyName
  override fun getShortCompanyName(): String? = delegate.shortCompanyName
  override fun getCompanyURL(): String? = delegate.companyURL
  override fun getFullVersion(): String = delegate.fullVersion
  override fun getStrictVersion(): String = delegate.strictVersion
  override fun getFullApplicationName(): String? = delegate.fullApplicationName
  override fun getMajorReleaseBuildDate() = delegate.majorReleaseBuildDate
  override fun getApplicationSvgIconUrl(): String = delegate.applicationSvgIconUrl
  override fun getSmallApplicationSvgIconUrl(): String = delegate.smallApplicationSvgIconUrl
  override fun getCopyrightStart(): String? = delegate.copyrightStart
  override fun isMajorEAP(): Boolean = delegate.isMajorEAP()
  override fun isPreview(): Boolean = delegate.isPreview()
  override fun getFullIdeProductCode(): String? = delegate.fullIdeProductCode
  override fun getPluginManagerUrl(): String = delegate.pluginManagerUrl
  override fun usesJetBrainsPluginRepository(): Boolean = delegate.usesJetBrainsPluginRepository()
  override fun getPluginsListUrl(): String = delegate.pluginsListUrl
  override fun getPluginDownloadUrl(): String = delegate.pluginDownloadUrl
  override fun isEssentialPlugin(pluginId: String): Boolean = delegate.isEssentialPlugin(pluginId)
  override fun isEssentialPlugin(pluginId: PluginId): Boolean = delegate.isEssentialPlugin(pluginId)
  override fun getSubscriptionFormId(): String? = delegate.subscriptionFormId
  override fun areSubscriptionTipsAvailable(): Boolean = delegate.areSubscriptionTipsAvailable()
  override fun getApiVersionAsNumber(): BuildNumber = delegate.apiVersionAsNumber
  override fun getEssentialPluginIds(): List<PluginId> = delegate.essentialPluginIds
  override fun getDefaultLightLaf(): String? = delegate.defaultLightLaf
  override fun getDefaultClassicLightLaf(): String? = delegate.defaultClassicLightLaf
  override fun getDefaultDarkLaf(): String? = delegate.defaultDarkLaf
  override fun getDefaultClassicDarkLaf(): String? = delegate.defaultClassicDarkLaf
  override fun isEAP(): Boolean = delegate.isEAP
  override fun getSplashImageUrl(): String? = delegate.splashImageUrl
  override fun isSimplifiedSplashSupported(): Boolean = delegate.isSimplifiedSplashSupported
}
