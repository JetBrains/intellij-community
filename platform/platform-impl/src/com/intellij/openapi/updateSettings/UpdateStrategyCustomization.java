/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.updateSettings;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.updateSettings.impl.ChannelStatus;
import com.intellij.openapi.util.BuildNumber;
import org.jetbrains.annotations.NotNull;

/**
 * Override this service in your IDE to customize update behavior. It isn't supposed to be overridden in plugins.
 */
public class UpdateStrategyCustomization {
  public static UpdateStrategyCustomization getInstance() {
    return ServiceManager.getService(UpdateStrategyCustomization.class);
  }

  public boolean forceEapUpdateChannelForEapBuilds() {
    return true;
  }

  public boolean isChannelActive(@NotNull ChannelStatus channel) {
    return channel != ChannelStatus.MILESTONE;
  }

  /**
   * Returns {@code true} if the both passed builds correspond to the same major version of the IDE. The platform uses this method when several
   * new builds are available, to suggest updating to the build from the same major version, i.e. IntelliJ IDEA 2018.2.5 will suggest to
   * update to 2018.2.6, not to 2018.3.
   * <br>
   * Override this method if major versions of your IDE doesn't directly correspond to major version of the IntelliJ platform.
   */
  public boolean haveSameMajorVersion(@NotNull BuildNumber build1, @NotNull BuildNumber build2) {
    return build1.getBaselineVersion() == build2.getBaselineVersion();
  }

  /**
   * Returns {@code true} if version of {@code candidateBuild} is newer than version of {@code currentBuild} so it may be suggested as an update.
   * <br>
   * If you use composite version numbers in your IDE (e.g. to be able to release new versions in parallel with bugfix updates for older versions)
   * and append them to the build number of the platform they are based on, it may happen that a newer version of your IDE is based on an
   * older version of the platform, so a bug fix update for an old version will have build number greater than the build number of a new version.
   * In this case you need to override this method and compare components of build numbers which correspond to your IDE's version to ensure that
   * the old version won't be suggested as an update to the new version.
   */
  public boolean isNewerVersion(@NotNull BuildNumber candidateBuild, @NotNull BuildNumber currentBuild) {
    return candidateBuild.compareTo(currentBuild) > 0;
  }
}