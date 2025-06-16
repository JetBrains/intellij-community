// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.util;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.util.NlsSafe;

import java.util.Set;

public final class GradleConstants {
  public static final ProjectSystemId SYSTEM_ID = new ProjectSystemId("GRADLE");

  public static final @NlsSafe String GRADLE_NAME = "Gradle";

  public static final String EXTENSION = "gradle";
  public static final String DEFAULT_SCRIPT_NAME = "build.gradle";
  public static final String KOTLIN_DSL_SCRIPT_NAME = "build.gradle.kts";
  public static final String KOTLIN_DSL_SCRIPT_EXTENSION = "gradle.kts";
  public static final String SETTINGS_FILE_NAME = "settings.gradle";
  public static final String KOTLIN_DSL_SETTINGS_FILE_NAME = "settings.gradle.kts";
  public static final String DECLARATIVE_EXTENSION = "gradle.dcl";
  public static final String DECLARATIVE_SCRIPT_NAME = "build.gradle.dcl";
  public static final String DECLARATIVE_SETTINGS_FILE_NAME = "settings.gradle.dcl";

  public static final String[] BUILD_FILE_EXTENSIONS = {
    EXTENSION, KOTLIN_DSL_SCRIPT_EXTENSION, DECLARATIVE_EXTENSION
  };

  public static final String[] KNOWN_GRADLE_SETTINGS_FILES = {
    SETTINGS_FILE_NAME, KOTLIN_DSL_SETTINGS_FILE_NAME, DECLARATIVE_SETTINGS_FILE_NAME
  };

  public static final Set<String> KNOWN_GRADLE_FILES = Set.of(
    DEFAULT_SCRIPT_NAME, KOTLIN_DSL_SCRIPT_NAME, DECLARATIVE_SCRIPT_NAME,
    SETTINGS_FILE_NAME, KOTLIN_DSL_SETTINGS_FILE_NAME, DECLARATIVE_SETTINGS_FILE_NAME
  );

  public static final String USER_HOME_PROPERTY_KEY = "user.home";
  public static final String GRADLE_USER_HOME_ENV_KEY = "GRADLE_USER_HOME";
  public static final String GRADLE_USER_HOME_PROPERTY_KEY = "gradle.user.home";

  /**
   * @deprecated renamed, use GradleConstants#SYSTEM_DIRECTORY_PATH_KEY instead
   */
  @Deprecated
  public static final String SYSTEM_DIRECTORY_PATH_KEY = GRADLE_USER_HOME_ENV_KEY;

  public static final String TOOL_WINDOW_TOOLBAR_PLACE = "GRADLE_SYNC_CHANGES_TOOLBAR";

  public static final String OFFLINE_MODE_CMD_OPTION = "--offline";
  public static final String INIT_SCRIPT_CMD_OPTION = "--init-script";
  public static final String INCLUDE_BUILD_CMD_OPTION = "--include-build";

  public static final String GRADLE_SOURCE_SET_MODULE_TYPE_KEY = "sourceSet";

  public static final String TESTS_ARG_NAME = "--tests";

  public static final String BUILD_SRC_NAME = "buildSrc";

  public static final String GRADLE_PROPERTIES_FILE_NAME = "gradle.properties";
  public static final String GRADLE_LOCAL_PROPERTIES_FILE_NAME = "config.properties";
  public static final String GRADLE_DAEMON_JVM_PROPERTIES_FILE_NAME = "gradle-daemon-jvm.properties";

  public static final String GRADLE_DIR_NAME = "gradle";
  public static final String GRADLE_CACHE_DIR_NAME = ".gradle";

  private GradleConstants() { }
}
