package org.jetbrains.plugins.gradle.util;

import org.jetbrains.annotations.NonNls;

/**
 * @author Denis Zhdanov
 * @since 4/10/12 3:01 PM
 */
@SuppressWarnings("UseOfArchaicSystemPropertyAccessors")
public class GradleEnvironment {

  @NonNls public static final boolean DEBUG_GRADLE_HOME_PROCESSING = Boolean.getBoolean("gradle.debug.home.processing");

  private GradleEnvironment() {
  }
}
