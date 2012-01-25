package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.config.GradleToolWindowPanel;
import org.jetbrains.plugins.gradle.diff.*;
import org.jetbrains.plugins.gradle.model.GradleLibraryDependency;
import org.jetbrains.plugins.gradle.model.GradleModule;
import org.jetbrains.plugins.gradle.model.Named;
import org.jetbrains.plugins.gradle.ui.GradleIcons;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * UI control for showing difference between the gradle and intellij project structure.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:58 PM
 */
public class GradleProjectStructureChangesPanel extends GradleToolWindowPanel {

  /**
   * <pre>
   *     ...
   *      |_module     &lt;- module's name is a key
   *          |_...
   *          |_dependencies   &lt;- dependencies holder node is a value
   *                  |_dependency1
   *                  |_dependency2
   * </pre>
   */
  private final Map<String, DefaultMutableTreeNode> myModuleDependencies = new HashMap<String, DefaultMutableTreeNode>();
  private final Map<String, DefaultMutableTreeNode> myModules            = new HashMap<String, DefaultMutableTreeNode>();
  
  private final GradleProjectStructureChangesModel myChangesModel;

  private DefaultTreeModel myTreeModel;
  private JPanel           myContent;

  public GradleProjectStructureChangesPanel(@NotNull Project project, @NotNull GradleProjectStructureChangesModel model) {
    super(project, GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE);
    myChangesModel = model;
    myChangesModel.addListener(new GradleProjectStructureChangeListener() {
      @Override
      public void onChanges(@NotNull final Collection<GradleProjectStructureChange> changes) {
        UIUtil.invokeLaterIfNeeded(new Runnable() {
          @Override
          public void run() {
            updateTree(changes); 
          }
        });
      }
    });
    rebuildTree();
  }

  @NotNull
  private DefaultTreeModel init() {
    myContent = new JPanel(new GridBagLayout());
    DefaultTreeModel treeModel = new DefaultTreeModel(null);
    Tree tree = new Tree(treeModel);

    GridBagConstraints constraints = new GridBagConstraints();
    constraints.fill = GridBagConstraints.BOTH;
    constraints.weightx = constraints.weighty = 1;
    myContent.add(tree, constraints);
    return treeModel;
  }

  private void rebuildTree() {
    myModules.clear();
    myModuleDependencies.clear();
    
    DefaultMutableTreeNode root = new DefaultMutableTreeNode(buildDescriptor(getProject()));
    final Module[] modules = ModuleManager.getInstance(getProject()).getModules();
    RootPolicy<LibraryOrderEntry> policy = new RootPolicy<LibraryOrderEntry>() {
      @Override
      public LibraryOrderEntry visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, LibraryOrderEntry value) {
        return libraryOrderEntry;
      }
    };
    for (Module module : modules) {
      final DefaultMutableTreeNode moduleNode = new DefaultMutableTreeNode(buildDescriptor(module));
      myModules.put(module.getName(), moduleNode); // Assuming that module names are unique.
      List<LibraryOrderEntry> libraryDependencies = new ArrayList<LibraryOrderEntry>();
      final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
      for (OrderEntry orderEntry : moduleRootManager.getOrderEntries()) {
        final LibraryOrderEntry library = orderEntry.accept(policy, null);
        if (library != null) {
          libraryDependencies.add(library);
        }
      }
      if (!libraryDependencies.isEmpty()) {
        DefaultMutableTreeNode dependenciesNode = getDependenciesNode(module.getName());
        for (LibraryOrderEntry dependency : libraryDependencies) {
          dependenciesNode.add(new DefaultMutableTreeNode(buildDescriptor(dependency)));
        }
      }
      root.add(moduleNode);
    }
    
    myTreeModel.setRoot(root);
    updateTree(myChangesModel.getChanges());
  }

  private static GradleProjectStructureNodeDescriptor<Object> buildDescriptor(@NotNull String name, Icon icon) {
    return new GradleProjectStructureNodeDescriptor<Object>(new Object(), name, icon);
  }

  private static GradleProjectStructureNodeDescriptor<Named> buildDescriptor(@NotNull Named entity, @NotNull Icon icon) {
    return new GradleProjectStructureNodeDescriptor<Named>(entity, entity.getName(), icon);
  } 

  private static GradleProjectStructureNodeDescriptor<Project> buildDescriptor(@NotNull Project project) {
    return new GradleProjectStructureNodeDescriptor<Project>(project, project.getName(), GradleIcons.PROJECT_ICON);
  }

  private static GradleProjectStructureNodeDescriptor<Module> buildDescriptor(@NotNull Module module) {
    return new GradleProjectStructureNodeDescriptor<Module>(module, module.getName(), GradleIcons.MODULE_ICON);
  }

  private static GradleProjectStructureNodeDescriptor<GradleModule> buildDescriptor(@NotNull GradleModule module) {
    return new GradleProjectStructureNodeDescriptor<GradleModule>(module, module.getName(), GradleIcons.MODULE_ICON);
  }

  private static GradleProjectStructureNodeDescriptor<LibraryOrderEntry> buildDescriptor(@NotNull LibraryOrderEntry library) {
    return new GradleProjectStructureNodeDescriptor<LibraryOrderEntry>(library, library.getPresentableName(), GradleIcons.LIB_ICON);
  }

  private DefaultMutableTreeNode getDependenciesNode(@NotNull String moduleName) {
    final DefaultMutableTreeNode cached = myModuleDependencies.get(moduleName);
    if (cached != null) {
      return cached;
    }
    DefaultMutableTreeNode moduleNode = myModules.get(moduleName);
    if (moduleNode == null) {
      moduleNode = new DefaultMutableTreeNode(buildDescriptor(moduleName, GradleIcons.MODULE_ICON));
      myModules.put(moduleName, moduleNode);
    }

    DefaultMutableTreeNode result = new DefaultMutableTreeNode(GradleBundle.message("gradle.project.structure.tree.node.dependencies"));
    moduleNode.add(result);
    myModuleDependencies.put(moduleName, result);
    return result;
  }
  
  @NotNull
  @Override
  protected JComponent buildContent() {
    myTreeModel = init();
    return myContent;
  }

  @Override
  protected void updateContent() {
    // TODO den implement
    int i = 1;
  }

  private void updateTree(@NotNull Collection<GradleProjectStructureChange> changes) {
    for (GradleProjectStructureChange change : changes) {
      change.invite(new GradleProjectStructureChangeVisitor() {
        @Override
        public void visit(@NotNull GradleRenameChange change) {
          // TODO den implement 
        }

        @Override
        public void visit(@NotNull GradleProjectStructureChange change) {
          // TODO den implement 
        }

        @Override
        public void visit(@NotNull GradleModulePresenceChange change) {
          // TODO den implement 
        }

        @Override
        public void visit(@NotNull GradleLibraryDependencyPresenceChange change) {
          String moduleName;
          GradleProjectStructureNodeDescriptor<?> descriptor;
          final GradleLibraryDependency gradleEntity = change.getGradleEntity();
          final LibraryOrderEntry intellijEntity = change.getIntellijEntity();
          final Object missingEntity;
          if (gradleEntity == null && intellijEntity == null) {
            // Never expect to be here.
            assert false;
          }
          
          if (gradleEntity == null) {
            // Particular library dependency is added at the intellij side.
            moduleName = intellijEntity.getOwnerModule().getName();
            descriptor = buildDescriptor(intellijEntity);
            descriptor.setAttributes(GradleTextAttributes.INTELLIJ_LOCAL_CHANGE);
            missingEntity = intellijEntity;
          }
          else  {
            // Particular library dependency is added at the gradle side.
            moduleName = gradleEntity.getOwnerModule().getName();
            descriptor = buildDescriptor(gradleEntity, GradleIcons.LIB_ICON);
            descriptor.setAttributes(GradleTextAttributes.GRADLE_LOCAL_CHANGE);
            missingEntity = gradleEntity;
          }
          final DefaultMutableTreeNode dependenciesNode = getDependenciesNode(moduleName);
          for (int i = 0, max = dependenciesNode.getChildCount(); i < max; i++) {
            final DefaultMutableTreeNode child = (DefaultMutableTreeNode)dependenciesNode.getChildAt(i);
            GradleProjectStructureNodeDescriptor<?> d = (GradleProjectStructureNodeDescriptor<?>)child.getUserObject();
            if (missingEntity.equals(d.getElement())) {
              d.setAttributes(descriptor.getAttributes());
              return;
            }
          }
          DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(descriptor);
          dependenciesNode.add(newNode);
          myTreeModel.nodeStructureChanged(dependenciesNode);
        }
      });
    }
  }
}
