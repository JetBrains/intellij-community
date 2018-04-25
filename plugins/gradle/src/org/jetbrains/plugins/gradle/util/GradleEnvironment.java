package org.jetbrains.plugins.gradle.util;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;

/**
 * @author Denis Zhdanov
 * @since 4/10/12 3:01 PM
 */
@SuppressWarnings("UseOfArchaicSystemPropertyAccessors")
public class GradleEnvironment {

  @NonNls public static final boolean DEBUG_GRADLE_HOME_PROCESSING = Boolean.getBoolean("gradle.debug.home.processing");
  @NonNls public static final boolean ADJUST_USER_DIR = Boolean.getBoolean("gradle.adjust.userdir");
  @ApiStatus.Experimental // todo should be replaced with the related event level setting at the gradle tooling api
  @NonNls public static final boolean GRADLE_PROGRESS_VERBOSE_EVENTS = Boolean.getBoolean("gradle.progress.verbose.events");

  public static class Headless {
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
