package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleDependency;
import org.jetbrains.plugins.gradle.importing.model.GradleEntity;
import org.jetbrains.plugins.gradle.importing.model.GradleModule;
import org.jetbrains.plugins.gradle.importing.model.GradleProject;
import org.jetbrains.plugins.gradle.importing.wizard.AbstractImportFromGradleWizardStep;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.Collection;
import java.util.Map;

/**
 * Is assumed to address the following concerns:
 * <pre>
 * <ul>
 *   <li>shows target project structure retrieved from gradle (project and modules);</li>
 *   <li>allow to adjust settings of the project and modules prior to importing;</li>
 * </ul>
 * </pre>
 * 
 * @author Denis Zhdanov
 * @since 8/2/11 12:31 PM
 */
public class GradleAdjustImportSettingsStep extends AbstractImportFromGradleWizardStep {

  private static final String EMPTY_CARD_NAME = "EMPTY";

  private final GradleProjectStructureFactory myFactory            = GradleProjectStructureFactory.INSTANCE;
  private final JPanel                        myComponent          = new JPanel(new GridLayout(1, 2));
  private final DefaultTreeModel              myTreeModel          = new DefaultTreeModel(new DefaultMutableTreeNode("unnamed"));
  private final Tree                          myTree               = new Tree(myTreeModel);
  private final CardLayout                    mySettingsCardLayout = new CardLayout();
  private final JPanel                        mySettingsPanel      = new JPanel(mySettingsCardLayout);
  private final Map<Object, String>           myCards              = new HashMap<Object, String>();

  public GradleAdjustImportSettingsStep(WizardContext context) {
    super(context);

    // Init.
    myTree.setShowsRootHandles(true);

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        Object node = myTree.getLastSelectedPathComponent();
        String cardName = myCards.get(node);
        if (cardName == null) {
          cardName = EMPTY_CARD_NAME;
        }
        mySettingsCardLayout.show(mySettingsPanel, cardName);
      }
    });
    
    // Setup GUI.
    JPanel leftPanel = new JPanel(new GridBagLayout());
    JPanel rightPanel = new JPanel(new GridBagLayout());
    
    GridBagConstraints constraints = new GridBagConstraints();
    constraints.weightx = 1;
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(5, 5, 5, 5);
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    leftPanel.add(new JLabel(GradleBundle.message("gradle.import.label.project.structure")), constraints);
    
    constraints = new GridBagConstraints();
    constraints.weightx = constraints.weighty = 1;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    leftPanel.add(new JBScrollPane(myTree), constraints);
    
    myComponent.add(leftPanel);

    constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets = new Insets(5, 10, 5, 5);
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    rightPanel.add(new JLabel(GradleBundle.message("gradle.import.label.details")), constraints);

    constraints = new GridBagConstraints();
    constraints.weightx = constraints.weighty = 1;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.insets.left = 10;
    rightPanel.add(mySettingsPanel, constraints);
    
    myComponent.add(rightPanel);
  }

// this is the first
//
//   }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }

  @Override
  public void updateStep() {
    myCards.clear();
    mySettingsPanel.removeAll();
    mySettingsPanel.add(new JPanel(), EMPTY_CARD_NAME);
    
    GradleProject project = getBuilder().getGradleProject();
    if (project == null) {
      throw new IllegalStateException(String.format(
        "Can't init 'adjust importing settings' step. Reason: no project is defined. Context: '%s', builder: '%s'",
        getContext(), getBuilder()
      ));
    }

    int counter = 0;
    DefaultMutableTreeNode root = buildNode(project, counter++);
    for (GradleModule module : project.getModules()) {
      DefaultMutableTreeNode moduleNode = buildNode(module, counter++);
      root.add(moduleNode);
      Collection<GradleDependency> dependencies = module.getDependencies();
      if (!dependencies.isEmpty()) {
        DefaultMutableTreeNode dependenciesNode
          = new DefaultMutableTreeNode(GradleBundle.message("gradle.import.structure.tree.node.dependencies"));
        for (GradleDependency dependency : dependencies) {
          dependenciesNode.add(buildNode(dependency, counter++));
        }
        moduleNode.add(dependenciesNode);
      } 
    }
    myTreeModel.setRoot(root);
    myTree.setSelectionPath(new TreePath(root));
  }

  private <T extends GradleEntity> DefaultMutableTreeNode buildNode(@NotNull T entity, int counter) {
    DefaultMutableTreeNode result = new DefaultMutableTreeNode(myFactory.buildDescriptor(entity));
    GradleProjectStructureNodeSettings settings = myFactory.buildSettings(entity);
    String cardName = String.valueOf(counter);
    myCards.put(result, cardName);
    mySettingsPanel.add(settings.getComponent(), cardName);
    return result;
  }
  
  @Override
  public void updateDataModel() {
    // TODO den implement 
  }
}
