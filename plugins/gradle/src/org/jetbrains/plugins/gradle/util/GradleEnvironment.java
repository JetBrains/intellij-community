// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.util;

import org.jetbrains.annotations.NonNls;

/**
 * @author Denis Zhdanov
 */
public final class GradleEnvironment {

  @NonNls public static final boolean DEBUG_GRADLE_HOME_PROCESSING = Boolean.getBoolean("gradle.debug.home.processing");
  @NonNls public static final boolean ADJUST_USER_DIR = Boolean.getBoolean("gradle.adjust.userdir");

  public static final class Headless {
    @NonNls public static final String GRADLE_DISTRIBUTION_TYPE = System.getProperty("idea.gradle.distributionType");
    @NonNls public static final String GRADLE_HOME = System.getProperty("idea.gradle.home");
    @NonNls public static final String GRADLE_VM_OPTIONS = System.getProperty("idea.gradle.vmOptions");
    @NonNls public static final String GRADLE_OFFLINE = System.getProperty("idea.gradle.offline");
    @NonNls public static final String GRADLE_SERVICE_DIRECTORY = System.getProperty("idea.gradle.serviceDirectory");

    private Headless() {
    }
  }


  private GradleEnvironment() {
  }
}
