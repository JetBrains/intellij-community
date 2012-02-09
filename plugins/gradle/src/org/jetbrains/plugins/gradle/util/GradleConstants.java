package org.jetbrains.plugins.gradle.util;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor;

/**
 * Holds object representation of icons used at the <code>Gradle</code> plugin.
 * 
 * @author Denis Zhdanov
 * @since 8/1/11 3:10 PM
 */
public class GradleConstants {

  @NonNls public static final String EXTENSION           = "gradle";
  @NonNls public static final String DEFAULT_SCRIPT_NAME = "build.gradle";

  @NonNls public static final String TOOL_WINDOW_TOOLBAR_PLACE = "GRADLE_SYNC_CHANGES_TOOLBAR";
  @NonNls public static final String SYNC_TREE_PLACE           = "GRADLE_SYNC_TREE_PLACE";
  
  @NonNls public static final String ACTION_GROUP_SYNC_TREE = "Gradle.SyncTreeGroup";
  
  
  public static final GradleProjectStructureNodeDescriptor<String> DEPENDENCIES_NODE_DESCRIPTOR
    = new GradleProjectStructureNodeDescriptor<String>(
    GradleBundle.message("gradle.project.structure.tree.node.dependencies"),
    GradleBundle.message("gradle.project.structure.tree.node.dependencies"),
    null
  );

  public static final GradleProjectStructureNodeDescriptor<String> MODULES_NODE_DESCRIPTOR
    = new GradleProjectStructureNodeDescriptor<String>(
    GradleBundle.message("gradle.import.structure.tree.node.modules"),
    GradleBundle.message("gradle.import.structure.tree.node.modules"),
    null
  );

  public static final GradleProjectStructureNodeDescriptor<String> LIBRARIES_NODE_DESCRIPTOR
    = new GradleProjectStructureNodeDescriptor<String>(
    GradleBundle.message("gradle.import.structure.tree.node.libraries"),
    GradleBundle.message("gradle.import.structure.tree.node.libraries"),
    null
  );

  private GradleConstants() {
  }
}
