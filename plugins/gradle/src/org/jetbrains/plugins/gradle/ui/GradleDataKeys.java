package org.jetbrains.plugins.gradle.ui;

import com.intellij.openapi.actionSystem.DataKey;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 2/7/12 11:19 AM
 */
public class GradleDataKeys {

  /** Key for obtaining currently selected nodes at the gradle 'sync project structure' tree. */
  public static final DataKey<Collection<GradleProjectStructureNode<?>>> SYNC_TREE_SELECTED_NODE
    = DataKey.create("gradle.sync.tree.node.selected");

  /** Key for obtaining node under mouse cursor at the gradle 'sync project structure' tree. */
  public static final DataKey<GradleProjectStructureNode<?>> SYNC_TREE_NODE_UNDER_MOUSE
    = DataKey.create("gradle.sync.tree.node.under.mouse");

  private GradleDataKeys() {
  }
}
