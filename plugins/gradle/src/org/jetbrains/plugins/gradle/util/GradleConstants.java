package org.jetbrains.plugins.gradle.util;

import com.intellij.openapi.util.IconLoader;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * Holds object representation of icons used at the <code>Gradle</code> plugin.
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 3:10 PM
 */
public class GradleConstants {

  @NonNls public static final String EXTENSION                 = "gradle";
  @NonNls public static final String DEFAULT_SCRIPT_NAME       = "build.gradle";
  @NonNls public static final String TOOL_WINDOW_TOOLBAR_PLACE = "GRADLE_SYNC_CHANGES_TOOLBAR";

  private GradleConstants() {
  }
}
