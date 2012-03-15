package org.jetbrains.plugins.gradle.util;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.gradle.model.id.GradleSyntheticId;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor;

/**
 * Holds object representation of icons used at the <code>Gradle</code> plugin.
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 3:10 PM
 */
public class GradleConstants {

  @NonNls public static final String NEWLY_IMPORTED_PROJECT = "gradle.newly.imported";
  
  @NonNls public static final String EXTENSION           = "gradle";
  @NonNls public static final String DEFAULT_SCRIPT_NAME = "build.gradle";
  
  @NonNls public static final String TOOL_WINDOW_ID = "JetGradle";

  @NonNls public static final String TOOL_WINDOW_TOOLBAR_PLACE        = "GRADLE_SYNC_CHANGES_TOOLBAR";
  @NonNls public static final String SYNC_TREE_CONTEXT_MENU_PLACE     = "GRADLE_SYNC_TREE_CONTEXT_MENU_PLACE";
  @NonNls public static final String SYNC_TREE_FLOATING_TOOLBAR_PLACE = "GRADLE_SYNC_TREE_FLOATING_TOOLBAR_PLACE";
  @NonNls public static final String SYNC_TREE_FILTER_PLACE           = "GRADLE_SYNC_TREE_FILTER_PLACE";
  
  @NonNls public static final String ACTION_GROUP_SYNC_TREE = "Gradle.SyncTreeGroup";

  @NonNls public static final String HELP_TOPIC_IMPORT_SELECT_PROJECT_STEP = "reference.dialogs.new.project.import.gradle.page1";
  @NonNls public static final String HELP_TOPIC_ADJUST_SETTINGS_STEP       = "reference.dialogs.new.project.import.gradle.page2";
  @NonNls public static final String HELP_TOPIC_TOOL_WINDOW                = "reference.toolwindows.gradle";
  
  public static final GradleProjectStructureNodeDescriptor<GradleSyntheticId> DEPENDENCIES_NODE_DESCRIPTOR
    = GradleUtil.buildSyntheticDescriptor(GradleBundle.message("gradle.project.structure.tree.node.dependencies"));

  public static final GradleProjectStructureNodeDescriptor<GradleSyntheticId> MODULES_NODE_DESCRIPTOR
    = GradleUtil.buildSyntheticDescriptor(GradleBundle.message("gradle.import.structure.tree.node.modules"));

  public static final GradleProjectStructureNodeDescriptor<GradleSyntheticId> LIBRARIES_NODE_DESCRIPTOR
    = GradleUtil.buildSyntheticDescriptor(GradleBundle.message("gradle.import.structure.tree.node.libraries"));

  private GradleConstants() {
  }
}
