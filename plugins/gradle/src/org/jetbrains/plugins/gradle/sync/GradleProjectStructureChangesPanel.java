package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.project.Project;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleToolWindowPanel;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureHelper;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

/**
 * UI control for showing difference between the gradle and intellij project structure.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:58 PM
 */
public class GradleProjectStructureChangesPanel extends GradleToolWindowPanel {

  private GradleProjectStructureTreeModel myTreeModel;
  private JPanel                          myContent;

  public GradleProjectStructureChangesPanel(@NotNull Project project,
                                            @NotNull GradleProjectStructureChangesModel model,
                                            @NotNull GradleProjectStructureHelper projectStructureHelper)
  {
    super(project, projectStructureHelper, GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE);
    model.addListener(new GradleProjectStructureChangeListener() {
      @Override
      public void onChanges(@NotNull final Collection<GradleProjectStructureChange> oldChanges,
                            @NotNull final Collection<GradleProjectStructureChange> currentChanges)
      {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            myTreeModel.update(currentChanges);
            myTreeModel.pruneObsoleteNodes(ContainerUtil.subtract(oldChanges, currentChanges));
          }
        });
      }
    });
  }

  private void init() {
    myContent = new JPanel(new GridBagLayout());
    myTreeModel = new GradleProjectStructureTreeModel(getProject(), getProjectStructureHelper());
    Tree tree = new Tree(myTreeModel);

    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = constraints.weighty = 1;
    myContent.add(tree, constraints);
    myContent.setBackground(tree.getBackground());
  }
  
  @NotNull
  @Override
  protected JComponent buildContent() {
    init();
    return myContent;
  }

  @Override
  protected void updateContent() {
    // TODO den implement
    int i = 1;
  }

}
