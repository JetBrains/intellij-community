package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import com.intellij.ide.util.projectWizard.WizardContext;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.GradleProjectImportBuilder;
import org.jetbrains.plugins.gradle.model.*;
import org.jetbrains.plugins.gradle.importing.wizard.AbstractImportFromGradleWizardStep;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
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
  
  private final Map<GradleProjectStructureNode, Pair<String, GradleProjectStructureNodeSettings>> myCards =
    new HashMap<GradleProjectStructureNode, Pair<String, GradleProjectStructureNodeSettings>>();
  
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

  @Override
  public JComponent getComponent() {
    return myComponent;
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
    GradleProject project = builder.getGradleProject();
    if (project == null) {
      throw new IllegalStateException(String.format(
        "Can't init 'adjust importing settings' step. Reason: no project is defined. Context: '%s', builder: '%s'",
        getWizardContext(), getBuilder()
      ));
    }

    Map<GradleEntity, Pair<String, Collection<GradleProjectStructureNode>>> entity2nodes
      = new HashMap<GradleEntity, Pair<String, Collection<GradleProjectStructureNode>>>();
    int counter = 0;
    DefaultMutableTreeNode root = buildNode(project, entity2nodes, counter++);

    List<GradleModule> modules = new ArrayList<GradleModule>(project.getModules());
    Collections.sort(modules, Named.COMPARATOR);
    List<MutableTreeNode> moduleNodes = new ArrayList<MutableTreeNode>();
    Map<GradleModule, GradleModule> moduleMappings = new HashMap<GradleModule, GradleModule>();
    
    GradleEntityCloneContext cloneContext = new GradleEntityCloneContext();
    for (GradleModule module : modules) {
      GradleModule moduleCopy = module.clone(cloneContext);
      moduleMappings.put(module, moduleCopy);
      DefaultMutableTreeNode moduleNode = buildNode(module, entity2nodes, counter++);
      moduleNodes.add(moduleNode);
      for (GradleContentRoot contentRoot : moduleCopy.getContentRoots()) {
        moduleNode.add(buildNode(contentRoot, entity2nodes, counter++));
      }
      Collection<GradleDependency> dependencies = module.getDependencies();
      if (!dependencies.isEmpty()) {
        DefaultMutableTreeNode dependenciesNode
          = new DefaultMutableTreeNode(GradleBundle.message("gradle.project.structure.tree.node.dependencies"));
        final List<GradleModuleDependency> moduleDependencies = new ArrayList<GradleModuleDependency>();
        final List<GradleLibraryDependency> libraryDependencies = new ArrayList<GradleLibraryDependency>();
        GradleEntityVisitor visitor = new GradleEntityVisitorAdapter() {
          @Override
          public void visit(@NotNull GradleModuleDependency dependency) {
            moduleDependencies.add(dependency);
          }

          @Override
          public void visit(@NotNull GradleLibraryDependency dependency) {
            libraryDependencies.add(dependency);
          }
        };
        for (GradleDependency dependency : dependencies) {
          dependency.invite(visitor);
        }
        Collections.sort(moduleDependencies, GradleModuleDependency.COMPARATOR);
        Collections.sort(libraryDependencies, Named.COMPARATOR);
        for (GradleModuleDependency dependency : moduleDependencies) {
          dependenciesNode.add(buildNode(dependency, entity2nodes, counter++));
        }
        for (GradleLibraryDependency dependency : libraryDependencies) {
          dependenciesNode.add(buildNode(dependency, entity2nodes, counter++));
        }
        moduleNode.add(dependenciesNode);
      }
    }

    myTreeModel.setRoot(root);
    myTree.setSelectionPath(new TreePath(root));
    
    Collection<? extends GradleLibrary> libraries = project.getLibraries();
    if (libraries.isEmpty()) {
      for (MutableTreeNode node : moduleNodes) {
        root.add(node);
      }
    }
    else {
      // Insert intermediate 'modules' and 'libraries' nodes if the project has both libraries and nodes.
      DefaultMutableTreeNode modulesNode = new DefaultMutableTreeNode(GradleBundle.message("gradle.import.structure.tree.node.modules"));
      for (MutableTreeNode node : moduleNodes) {
        modulesNode.add(node);
      }
      root.add(modulesNode);

      List<GradleLibrary> sortedLibraries = new ArrayList<GradleLibrary>(libraries);
      Collections.sort(sortedLibraries, Named.COMPARATOR);
      DefaultMutableTreeNode librariesNode = new DefaultMutableTreeNode(GradleBundle.message("gradle.import.structure.tree.node.libraries"));
      for (GradleLibrary library : sortedLibraries) {
        librariesNode.add(buildNode(library, entity2nodes, counter++));
      }
      root.add(librariesNode);

      myTree.expandPath(new TreePath(modulesNode.getPath()));
      myTree.expandPath(new TreePath(librariesNode.getPath()));
    }
    
    myTree.expandPath(new TreePath(root.getPath()));
    builder.setModuleMappings(moduleMappings);
  }

  private <T extends GradleEntity> DefaultMutableTreeNode buildNode(
    @NotNull T entity, @NotNull Map<GradleEntity, Pair<String, Collection<GradleProjectStructureNode>>> processed, int counter)
  {
    // We build tree node, its settings control and map them altogether. The only trick here is that nodes can reuse the same
    // settings control (e.g. more than one node may have the same library as a dependency, so, library dependency node for
    // every control will use the same settings control).
    GradleProjectStructureNode<T> result = new GradleProjectStructureNode<T>(myFactory.buildDescriptor(entity), GradleEntityType.SYNTHETIC);
    Pair<String, Collection<GradleProjectStructureNode>> pair = processed.get(entity);
    if (pair == null) {
      String cardName = String.valueOf(counter);
      List<GradleProjectStructureNode> nodes = new ArrayList<GradleProjectStructureNode>();
      nodes.add(result);
      processed.put(entity, new Pair<String, Collection<GradleProjectStructureNode>>(cardName, nodes));
      GradleProjectStructureNodeSettings settings = myFactory.buildSettings(entity, myTreeModel, nodes);
      myCards.put(result, new Pair<String, GradleProjectStructureNodeSettings>(cardName, settings));
      mySettingsPanel.add(settings.getComponent(), cardName);
    } 
    else {
      pair.second.add(result);
      for (GradleProjectStructureNode node : pair.second) {
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

    for (Map.Entry<GradleProjectStructureNode, Pair<String, GradleProjectStructureNodeSettings>> entry : myCards.entrySet()) {
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
