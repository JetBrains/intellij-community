// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.components.service
import com.intellij.openapi.updateSettings.impl.ChannelStatus
import com.intellij.openapi.updateSettings.impl.UpdateChannel
import com.intellij.openapi.util.BuildNumber
import com.intellij.openapi.util.NlsContexts

/**
 * Override this service in your IDE to customize update behavior. It isn't supposed to be overridden in plugins.
 */
open class UpdateStrategyCustomization {
  companion object {
    @JvmStatic
    fun getInstance(): UpdateStrategyCustomization = service()
  }

  open fun forceEapUpdateChannelForEapBuilds(): Boolean {
    return true
  }

  /**
   * Executed when IDE starts and provides a way to change current update channel. If a non-null value is returned, it'll be used as the current
   * update channel as is. If `null` is returned, the default customization will be applied. (By default the update channel is set to
   * ['EAP'][ChannelStatus.EAP] for EAP builds if [.forceEapUpdateChannelForEapBuilds] is `true`, and to
   * ['release'][ChannelStatus.RELEASE] for release builds on the first run.)
   */
  open fun changeDefaultChannel(currentChannel: ChannelStatus): ChannelStatus? {
    return null
  }

  open fun isChannelActive(channel: ChannelStatus): Boolean {
    return channel != ChannelStatus.MILESTONE && channel != ChannelStatus.BETA
  }

  /**
   * Returns `true` if the both passed builds correspond to the same major version of the IDE. The platform uses this method when several
   * new builds are available, to suggest updating to the build from the same major version, i.e. IntelliJ IDEA 2018.2.5 will suggest to
   * update to 2018.2.6, not to 2018.3.
   *
   * Override this method if major versions of your IDE doesn't directly correspond to major version of the IntelliJ platform.
   */
  open fun haveSameMajorVersion(build1: BuildNumber, build2: BuildNumber): Boolean {
    return build1.baselineVersion == build2.baselineVersion
  }

  /**
   * Returns `true` if version of `candidateBuild` is newer than version of `currentBuild` so it may be suggested as an update.
   *
   * If you use composite version numbers in your IDE (e.g. to be able to release new versions in parallel with bugfix updates for older versions)
   * and append them to the build number of the platform they are based on, it may happen that a newer version of your IDE is based on an
   * older version of the platform, so a bug fix update for an old version will have build number greater than the build number of a new version.
   * In this case you need to override this method and compare components of build numbers which correspond to your IDE's version to ensure that
   * the old version won't be suggested as an update to the new version.
   */
  open fun isNewerVersion(candidateBuild: BuildNumber, currentBuild: BuildNumber): Boolean {
    return candidateBuild > currentBuild
  }

  /**
   * Returns `true` if IDE should search for updates in [updateChannel] when the current update channel has [selectedChannel] status.
   */
  open fun isChannelApplicableForUpdates(updateChannel: UpdateChannel, selectedChannel: ChannelStatus): Boolean {
    return updateChannel.status >= selectedChannel
  }

  /**
   * Returns `true` if IDE may search for intermediate patches in builds from [updateChannel] when there is no direct patch from the current
   * to the new version.
   */
  open fun canBeUsedForIntermediatePatches(updateChannel: UpdateChannel, selectedChannel: ChannelStatus): Boolean {
    return true
  }

  /**
   * Returns `null` if user should be allowed to change the update channel in UI. If a non-null value is returned, it is shown in UI instead
   * of the channel chooser and user won't be able to change the update channel.
   */
  @NlsContexts.DetailedDescription
  open fun getChannelSelectionLockedMessage(): String? =
    if (ApplicationInfoEx.getInstanceEx().isMajorEAP && forceEapUpdateChannelForEapBuilds())
      IdeBundle.message("updates.settings.channel.locked")
    else null
}