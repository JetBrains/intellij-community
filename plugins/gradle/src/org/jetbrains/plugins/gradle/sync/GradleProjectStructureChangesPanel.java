package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.SimpleTree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleToolWindowPanel;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;

/**
 * // TODO den add doc
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:58 PM
 */
public class GradleProjectStructureChangesPanel extends GradleToolWindowPanel {
  
  private final GradleProjectStructureChangesModel myModel;

  public GradleProjectStructureChangesPanel(@NotNull Project project, @NotNull GradleProjectStructureChangesModel model) {
    super(project, GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE);
    myModel = model;
  }

  @NotNull
  @Override
  protected JComponent buildContent() {
    // TODO den implement
    return new SimpleTree();
    //return new JLabel("project-structure-change-content");
  }

  @Override
  protected void updateContent() {
    // TODO den implement 
  }
}
