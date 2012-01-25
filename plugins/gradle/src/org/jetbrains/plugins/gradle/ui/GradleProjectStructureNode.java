package org.jetbrains.plugins.gradle.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.tree.DefaultMutableTreeNode;

/**
 * @author Denis Zhdanov
 * @since 8/23/11 3:50 PM
 */
public class GradleProjectStructureNode extends DefaultMutableTreeNode {

  private final GradleProjectStructureNodeDescriptor myDescriptor;
  
  public GradleProjectStructureNode(@NotNull GradleProjectStructureNodeDescriptor descriptor) {
    super(descriptor);
    myDescriptor = descriptor;
  }

  @NotNull
  public GradleProjectStructureNodeDescriptor getDescriptor() {
    return myDescriptor;
  }
}
