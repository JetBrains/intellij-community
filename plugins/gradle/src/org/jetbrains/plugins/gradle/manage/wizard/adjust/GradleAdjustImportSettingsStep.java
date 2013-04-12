package org.jetbrains.plugins.gradle.manage.wizard.adjust;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import com.intellij.openapi.externalSystem.ui.ProjectStructureNode;
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.manage.GradleProjectImportBuilder;
import org.jetbrains.plugins.gradle.manage.wizard.AbstractImportFromGradleWizardStep;
import com.intellij.openapi.externalSystem.model.project.id.GradleSyntheticId;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.*;
import java.util.List;

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

  private final Map<ProjectStructureNode, Pair<String, GradleProjectStructureNodeSettings>> myCards =
    new HashMap<ProjectStructureNode, Pair<String, GradleProjectStructureNodeSettings>>();

  private boolean myOnValidateAttempt;

  public GradleAdjustImportSettingsStep(WizardContext context) {
    super(context);

    // Init.
    myTree.setShowsRootHandles(true);

    myTree.getSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {
      private boolean myIgnore;

      @SuppressWarnings("SuspiciousMethodCalls")
      @Override
      public void valueChanged(TreeSelectionEvent e) {
        if (myIgnore) {
          return;
        }

        TreePath oldPath = e.getOldLeadSelectionPath();
        if (oldPath == null) {
          onNodeChange();
          return;
        }
        Object oldNode = oldPath.getLastPathComponent();
        if (oldNode == null) {
          onNodeChange();
          return;
        }

        Pair<String, GradleProjectStructureNodeSettings> pair = myCards.get(oldNode);
        if (pair == null || pair.second.validate()) {
          onNodeChange();
          return;
        }

        myIgnore = true;
        try {
          myTree.getSelectionModel().setSelectionPath(oldPath);
        }
        finally {
          myIgnore = false;
        }
      }

      @SuppressWarnings("SuspiciousMethodCalls")
      private void onNodeChange() {
        Object node = myTree.getLastSelectedPathComponent();
        Pair<String, GradleProjectStructureNodeSettings> pair = myCards.get(node);
        String cardName = EMPTY_CARD_NAME;
        if (pair != null) {
          cardName = pair.first;
          pair.second.refresh();
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
    leftPanel.add(new JLabel(ExternalSystemBundle.message("gradle.import.label.project.structure")), constraints);
    
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
    rightPanel.add(new JLabel(ExternalSystemBundle.message("gradle.import.label.details")), constraints);

    constraints = new GridBagConstraints();
    constraints.weightx = constraints.weighty = 1;
    constraints.fill = GridBagConstraints.BOTH;
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.gridwidth = GridBagConstraints.REMAINDER;
    constraints.insets.left = 10;
    rightPanel.add(mySettingsPanel, constraints);
    
    myComponent.add(rightPanel);
  }

  @Override
  public JComponent getComponent() {
    return myComponent;
  }
  @Override
  public String getHelpId() {
    return GradleConstants.HELP_TOPIC_ADJUST_SETTINGS_STEP;
  }
  
  @Override
  public void updateStep() {
    if (myOnValidateAttempt) {
      // We assume that this method is called when project validation triggered by end-user fails (he or she pressed 'Next'/'Finish' 
      // button at the wizard and current state is invalid). So, there is no need to rebuild the model then.
      myOnValidateAttempt = false;
      return;
    }

    clear();

    GradleProjectImportBuilder builder = getBuilder();
    if (builder == null) {
      return;
    }
    ProjectData project = builder.getGradleProject();
    if (project == null) {
      throw new IllegalStateException(String.format(
        "Can't init 'adjust importing settings' step. Reason: no project is defined. Context: '%s', builder: '%s'",
        getWizardContext(), getBuilder()
      ));
    }

    Map<ProjectEntityData, Pair<String, Collection<ProjectStructureNode>>> entity2nodes
      = new HashMap<ProjectEntityData, Pair<String, Collection<ProjectStructureNode>>>();
    int counter = 0;
    ProjectStructureNode<ProjectEntityId> root = buildNode(project, entity2nodes, counter++);

    List<ModuleData> modules = new ArrayList<ModuleData>(project.getModules());
    Collections.sort(modules, Named.COMPARATOR);
    List<MutableTreeNode> moduleNodes = new ArrayList<MutableTreeNode>();
    
    for (ModuleData module : modules) {
      ProjectStructureNode<ProjectEntityId> moduleNode = buildNode(module, entity2nodes, counter++);
      moduleNodes.add(moduleNode);
      for (ContentRootData contentRoot : module.getContentRoots()) {
        moduleNode.add(buildNode(contentRoot, entity2nodes, counter++));
      }
      Collection<DependencyData> dependencies = module.getDependencies();
      if (!dependencies.isEmpty()) {
        ProjectStructureNode<GradleSyntheticId> dependenciesNode
          = new ProjectStructureNode<GradleSyntheticId>(GradleConstants.DEPENDENCIES_NODE_DESCRIPTOR);
        final List<ModuleDependencyData> moduleDependencies = new ArrayList<ModuleDependencyData>();
        final List<LibraryDependencyData> libraryDependencies = new ArrayList<LibraryDependencyData>();
        ExternalEntityVisitor visitor = new ExternalEntityVisitorAdapter() {
          @Override
          public void visit(@NotNull ModuleDependencyData dependency) {
            moduleDependencies.add(dependency);
          }

          @Override
          public void visit(@NotNull LibraryDependencyData dependency) {
            libraryDependencies.add(dependency);
          }
        };
        for (DependencyData dependency : dependencies) {
          dependency.invite(visitor);
        }
        Collections.sort(moduleDependencies, ModuleDependencyData.COMPARATOR);
        Collections.sort(libraryDependencies, Named.COMPARATOR);
        for (ModuleDependencyData dependency : moduleDependencies) {
          dependenciesNode.add(buildNode(dependency, entity2nodes, counter++));
        }
        for (LibraryDependencyData dependency : libraryDependencies) {
          dependenciesNode.add(buildNode(dependency, entity2nodes, counter++));
        }
        moduleNode.add(dependenciesNode);
      }
    }

    myTreeModel.setRoot(root);
    myTree.setSelectionPath(new TreePath(root));
    
    Collection<? extends LibraryData> libraries = project.getLibraries();
    if (libraries.isEmpty()) {
      for (MutableTreeNode node : moduleNodes) {
        root.add(node);
      }
    }
    else {
      // Insert intermediate 'modules' and 'libraries' nodes if the project has both libraries and nodes.
      ProjectStructureNode<GradleSyntheticId> modulesNode
        = new ProjectStructureNode<GradleSyntheticId>(GradleConstants.MODULES_NODE_DESCRIPTOR);
      for (MutableTreeNode node : moduleNodes) {
        modulesNode.add(node);
      }
      root.add(modulesNode);

      List<LibraryData> sortedLibraries = new ArrayList<LibraryData>(libraries);
      Collections.sort(sortedLibraries, Named.COMPARATOR);
      ProjectStructureNode<GradleSyntheticId> librariesNode
        = new ProjectStructureNode<GradleSyntheticId>(GradleConstants.LIBRARIES_NODE_DESCRIPTOR);
      for (LibraryData library : sortedLibraries) {
        librariesNode.add(buildNode(library, entity2nodes, counter++));
      }
      root.add(librariesNode);

      myTree.expandPath(new TreePath(modulesNode.getPath()));
      myTree.expandPath(new TreePath(librariesNode.getPath()));
    }
    
    myTree.expandPath(new TreePath(root.getPath()));
  }

  private ProjectStructureNode<ProjectEntityId> buildNode(
    @NotNull ProjectEntityData entity, @NotNull Map<ProjectEntityData, Pair<String, Collection<ProjectStructureNode>>> processed, int counter)
  {
    // We build tree node, its settings control and map them altogether. The only trick here is that nodes can reuse the same
    // settings control (e.g. more than one node may have the same library as a dependency, so, library dependency node for
    // every control will use the same settings control).
    ProjectStructureNode<ProjectEntityId> result = new ProjectStructureNode<ProjectEntityId>(myFactory.buildDescriptor(entity));
    Pair<String, Collection<ProjectStructureNode>> pair = processed.get(entity);
    if (pair == null) {
      String cardName = String.valueOf(counter);
      List<ProjectStructureNode> nodes = new ArrayList<ProjectStructureNode>();
      nodes.add(result);
      processed.put(entity, new Pair<String, Collection<ProjectStructureNode>>(cardName, nodes));
      GradleProjectStructureNodeSettings settings = myFactory.buildSettings(entity, myTreeModel, nodes);
      myCards.put(result, new Pair<String, GradleProjectStructureNodeSettings>(cardName, settings));
      mySettingsPanel.add(settings.getComponent(), cardName);
    } 
    else {
      pair.second.add(result);
      for (ProjectStructureNode node : pair.second) {
        Pair<String, GradleProjectStructureNodeSettings> settingsPair = myCards.get(node);
        if (settingsPair != null) {
          myCards.put(result, settingsPair);
          break;
        } 
      }
    }
    return result;
  }

  @SuppressWarnings("SuspiciousMethodCalls")
  @Override
  public boolean validate() throws ConfigurationException {
    GradleProjectImportBuilder builder = getBuilder();
    if (builder == null) {
      return false;
    }

    // Validate current card.
    Object node = myTree.getLastSelectedPathComponent();
    Pair<String, GradleProjectStructureNodeSettings> pair = myCards.get(node);
    if (pair != null && !pair.second.validate()) {
      myOnValidateAttempt = true;
      return false;
    }

    for (Map.Entry<ProjectStructureNode, Pair<String, GradleProjectStructureNodeSettings>> entry : myCards.entrySet()) {
      if (!entry.getValue().second.validate()) {
        myTree.getSelectionModel().setSelectionPath(new TreePath(entry.getKey().getPath()));
        myOnValidateAttempt = true;
        return false;
      }
    }

    builder.applyProjectSettings(getWizardContext());
    return true;
  }

  @Override
  public void updateDataModel() {
  }

  private void clear() {
    myCards.clear();
    mySettingsPanel.removeAll();
    mySettingsPanel.add(new JPanel(), EMPTY_CARD_NAME);
  }
}
