package org.jetbrains.plugins.gradle.ui;

import com.intellij.openapi.actionSystem.DataKey;

import java.util.Collection;

/**
 * @author Denis Zhdanov
 * @since 2/7/12 11:19 AM
 */
public class GradleDataKeys {

  /** Key for obtaining currently selected nodes at the gradle 'sync project structure' tree. */
  public static final DataKey<Collection<GradleProjectStructureNode<?>>> SYNC_TREE_NODE = DataKey.create("gradle.sync.tree.node");

  private GradleDataKeys() {
  }
}
