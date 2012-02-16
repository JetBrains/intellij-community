package org.jetbrains.plugins.gradle.util;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.id.GradleSyntheticId;
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
  
  
  public static final GradleProjectStructureNodeDescriptor<GradleSyntheticId> DEPENDENCIES_NODE_DESCRIPTOR
    = buildSyntheticDescriptor(GradleBundle.message("gradle.project.structure.tree.node.dependencies"));

  public static final GradleProjectStructureNodeDescriptor<GradleSyntheticId> MODULES_NODE_DESCRIPTOR
    = buildSyntheticDescriptor(GradleBundle.message("gradle.import.structure.tree.node.modules"));

  public static final GradleProjectStructureNodeDescriptor<GradleSyntheticId> LIBRARIES_NODE_DESCRIPTOR
    = buildSyntheticDescriptor(GradleBundle.message("gradle.import.structure.tree.node.libraries"));

  private GradleConstants() {
  }

  private static GradleProjectStructureNodeDescriptor<GradleSyntheticId> buildSyntheticDescriptor(@NotNull String text) {
    return new GradleProjectStructureNodeDescriptor<GradleSyntheticId>(new GradleSyntheticId(text), text, null);
  }
}
