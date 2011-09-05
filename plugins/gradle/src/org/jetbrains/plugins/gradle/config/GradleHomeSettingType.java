package org.jetbrains.plugins.gradle.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.PropertyKey;
import org.jetbrains.plugins.gradle.util.GradleBundle;

/**
 * Enumerates possible types of 'gradle home' location setting.
 * 
 * @author Denis Zhdanov
 * @since 9/2/11 3:58 PM
 */
public enum GradleHomeSettingType {
  
  /** User hasn't defined gradle location but the IDE discovered it automatically. */
  DEDUCED("gradle.home.setting.type.deduced"),
  
  /** User hasn't defined gradle location and the IDE was unable to discover it automatically. */
  UNKNOWN("gradle.home.setting.type.unknown"),
  
  /** User defined gradle location but it's incorrect. */
  EXPLICIT_INCORRECT("gradle.home.setting.type.explicit.incorrect"),
  
  EXPLICIT_CORRECT("gradle.home.setting.type.explicit.correct");
  
  private final String myDescription;

  GradleHomeSettingType(@NotNull @PropertyKey(resourceBundle = GradleBundle.PATH_TO_BUNDLE) String description) {
    myDescription = GradleBundle.message(description);
  }

  /**
   * @return    human-readable description of the current setting type
   */
  public String getDescription() {
    return myDescription;
  }
}
