package org.jetbrains.plugins.gradle.util;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Holds object representation of icons used at the <code>Gradle</code> plugin.
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 3:10 PM
 */
public class GradleConstants {

  @NotNull public static final ProjectSystemId SYSTEM_ID = new ProjectSystemId("GRADLE");

  @NonNls public static final String EXTENSION           = "gradle";
  @NonNls public static final String DEFAULT_SCRIPT_NAME = "build.gradle";

  public static final  String  SYSTEM_DIRECTORY_PATH_KEY    = "GRADLE_USER_HOME";

  @NonNls public static final String TOOL_WINDOW_ID = "JetGradle";

  @NonNls public static final String TOOL_WINDOW_TOOLBAR_PLACE    = "GRADLE_SYNC_CHANGES_TOOLBAR";
  @NonNls public static final String SYNC_TREE_CONTEXT_MENU_PLACE = "GRADLE_SYNC_TREE_CONTEXT_MENU_PLACE";
  @NonNls public static final String SYNC_TREE_FILTER_PLACE       = "GRADLE_SYNC_TREE_FILTER_PLACE";
  @NonNls public static final String TASKS_LIST_PLACE             = "TASKS_LIST_PLACE";
  @NonNls public static final String TASKS_CONTEXT_MENU_PLACE     = "GRADLE_TASKS_CONTEXT_MENU_PLACE";

  @NonNls public static final String ACTION_GROUP_SYNC_TREE = "Gradle.SyncTreeGroup";
  @NonNls public static final String ACTION_GROUP_TASKS     = "Gradle.TasksGroup";
  
  @NonNls public static final String HELP_TOPIC_IMPORT_SELECT_PROJECT_STEP = "reference.dialogs.new.project.import.gradle.page1";
  @NonNls public static final String HELP_TOPIC_ADJUST_SETTINGS_STEP       = "reference.dialogs.new.project.import.gradle.page2";
  @NonNls public static final String HELP_TOPIC_TOOL_WINDOW                = "reference.toolwindows.gradle";

  @NonNls public static final String ACTIVE_TOOL_WINDOW_TAB_KEY = "gradle.tool.window.active";

  @NonNls public static final String REGISTRY_RECENT_TASKS_NUMBER_KEY = "gradle.recent.tasks.number";
  @NonNls public static final String REGISTRY_DEBUG_ON_TASK_CLICK_KEY = "gradle.debug.task.on.double.click";

  private GradleConstants() {
  }
}
