// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.updateSettings.impl.*
import com.intellij.openapi.util.BuildNumber
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable

open class UpdateStrategyCustomization {

  companion object {
    @JvmStatic
    fun getInstance(): UpdateStrategyCustomization {
      return ApplicationManager.getApplication().getService(
        UpdateStrategyCustomization::class.java)
    }
  }

  /**
   * Executed when IDE starts and provides a way to change current update channel. If a non-null value is returned, it'll be used as the current
   * update channel as is. If {@code null} is returned, the default customization will be applied. (By default the update channel is set to
   * {@link ChannelStatus#EAP 'EAP'} for EAP builds if {@link #forceEapUpdateChannelForEapBuilds()} is {@code true}, and to
   * {@link ChannelStatus#RELEASE 'release'} for release builds on the first run.)
   */
  @Nullable
  open fun changeDefaultChannel(currentChannel: @NotNull ChannelStatus): ChannelStatus? {
    return null;
  }

  open fun forceEapUpdateChannelForEapBuilds(): Boolean {
    return true
  }

  open fun isChannelActive(channel: @NotNull ChannelStatus): Boolean {
    return channel != ChannelStatus.MILESTONE && channel != ChannelStatus.BETA
  }

  /**
   * Returns `true` if the both passed builds correspond to the same major version of the IDE. The platform uses this method when several
   * new builds are available, to suggest updating to the build from the same major version, i.e. IntelliJ IDEA 2018.2.5 will suggest to
   * update to 2018.2.6, not to 2018.3.
   * <br></br>
   * Override this method if major versions of your IDE doesn't directly correspond to major version of the IntelliJ platform.
   */
  open fun haveSameMajorVersion(build1: @NotNull BuildNumber, build2: @NotNull BuildNumber): Boolean {
    return build1.baselineVersion == build2.baselineVersion
  }

  /**
   * Returns `true` if version of `candidateBuild` is newer than version of `currentBuild` so it may be suggested as an update.
   * <br></br>
   * If you use composite version numbers in your IDE (e.g. to be able to release new versions in parallel with bugfix updates for older versions)
   * and append them to the build number of the platform they are based on, it may happen that a newer version of your IDE is based on an
   * older version of the platform, so a bug fix update for an old version will have build number greater than the build number of a new version.
   * In this case you need to override this method and compare components of build numbers which correspond to your IDE's version to ensure that
   * the old version won't be suggested as an update to the new version.
   */
  open fun isNewerVersion(candidateBuild: @NotNull BuildNumber, currentBuild: @NotNull BuildNumber): Boolean {
    return candidateBuild.compareTo(currentBuild) > 0
  }

  open fun isChannelApplicableForUpdates(updateChannel: UpdateChannel, selectedChannel: ChannelStatus): Boolean {
    return updateChannel.status >= selectedChannel
  }

  open fun isChannelApplicableForPatches(updateChannel: UpdateChannel, selectedChannel: ChannelStatus): Boolean {
    return true
  }

  open fun isChannelSelectionLocked(): Boolean {
    return ApplicationInfoEx.getInstanceEx().isMajorEAP && forceEapUpdateChannelForEapBuilds()
  }
}