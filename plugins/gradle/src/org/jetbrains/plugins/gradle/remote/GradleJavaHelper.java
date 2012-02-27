package org.jetbrains.plugins.gradle.remote;

import org.jetbrains.annotations.Nullable;

/**
 * Encapsulates functionality of deciding what java should be used by the gradle process.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/27/12 2:20 PM
 */
public class GradleJavaHelper {

  public static final String GRADLE_JAVA_HOME_KEY = "gradle.java.home";
  
  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  public String getJdkHome() {
    // TODO den implement
    return null;
  }
}
