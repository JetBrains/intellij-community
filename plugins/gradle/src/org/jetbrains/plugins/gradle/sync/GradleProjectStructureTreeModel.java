package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.diff.*;
import org.jetbrains.plugins.gradle.model.GradleLibraryDependency;
import org.jetbrains.plugins.gradle.model.Named;
import org.jetbrains.plugins.gradle.ui.GradleIcons;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.MutableTreeNode;
import javax.swing.tree.TreeNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Model for the target project structure tree used by the gradle integration.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/30/12 4:20 PM
 */
public class GradleProjectStructureTreeModel extends DefaultTreeModel {
  
  public static final GradleProjectStructureNodeDescriptor<Object> DEPENDENCIES_NODE_DESCRIPTOR = buildDescriptor(
    GradleBundle.message("gradle.project.structure.tree.node.dependencies"),
    null
  );
  
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

  private final TreeNode[] myNodeHolder  = new TreeNode[1];
  private final int[]      myIndexHolder = new int[1];

  private final Project        myProject;
  private final PlatformFacade myPlatformFacade;

  public GradleProjectStructureTreeModel(@NotNull Project project, @NotNull PlatformFacade platformFacade) {
    super(null);
    myProject = project;
    myPlatformFacade = platformFacade;
    rebuild();
  }

  public void rebuild() {
    myModuleDependencies.clear();
    myModules.clear();
    
    DefaultMutableTreeNode root = new DefaultMutableTreeNode(buildDescriptor(getProject()));
    final Collection<Module> modules = myPlatformFacade.getModules(getProject());
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
      for (OrderEntry orderEntry : myPlatformFacade.getOrderEntries(module)) {
        final LibraryOrderEntry libraryDependency = orderEntry.accept(policy, null);
        if (libraryDependency != null && !StringUtil.isEmpty(libraryDependency.getLibraryName())) {
          libraryDependencies.add(libraryDependency);
        }
      }
      if (!libraryDependencies.isEmpty()) {
        DefaultMutableTreeNode dependenciesNode = getDependenciesNode(module.getName());
        for (LibraryOrderEntry dependency : libraryDependencies) {
          dependenciesNode.add(new DefaultMutableTreeNode(buildDescriptor(dependency)));
        }
        moduleNode.add(dependenciesNode);
      }
      root.add(moduleNode);
    }

    setRoot(root);
  }
  
  @NotNull
  public Project getProject() {
    return myProject;
  }
  
  private static GradleProjectStructureNodeDescriptor<Object> buildDescriptor(@NotNull String name, @Nullable Icon icon) {
    return new GradleProjectStructureNodeDescriptor<Object>(new Object(), name, icon);
  }

  private static GradleProjectStructureNodeDescriptor<Named> buildDescriptor(@NotNull Named entity, @NotNull Icon icon) {
    return new GradleProjectStructureNodeDescriptor<Named>(entity, entity.getName(), icon);
  }

  private GradleProjectStructureNodeDescriptor<Project> buildDescriptor(@NotNull Project project) {
    return new GradleProjectStructureNodeDescriptor<Project>(project, project.getName(), myPlatformFacade.getProjectIcon());
  }

  private static GradleProjectStructureNodeDescriptor<Module> buildDescriptor(@NotNull Module module) {
    return new GradleProjectStructureNodeDescriptor<Module>(module, module.getName(), GradleIcons.MODULE_ICON);
  }

  private static GradleProjectStructureNodeDescriptor<LibraryOrderEntry> buildDescriptor(@NotNull LibraryOrderEntry library) {
    final String name = library.getLibraryName();
    assert name != null;
    return new GradleProjectStructureNodeDescriptor<LibraryOrderEntry>(library, name, GradleIcons.LIB_ICON);
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

    DefaultMutableTreeNode result = new DefaultMutableTreeNode(DEPENDENCIES_NODE_DESCRIPTOR);
    moduleNode.add(result);
    myModuleDependencies.put(moduleName, result);
    
    return result;
  }

  /**
   * Asks current model to update its state in accordance with the given changes.
   * 
   * @param changes  collections that contains all changes between the current gradle and intellij project structures
   */
  public void update(@NotNull Collection<GradleProjectStructureChange> changes) {
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
            if (intellijEntity.getLibraryName() == null) {
              return;
            }
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
              nodeStructureChanged(child);
              return;
            }
          }
          DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(descriptor);
          dependenciesNode.add(newNode);
          nodeStructureChanged(dependenciesNode);
        }
      });
    }
  }

  /**
   * Asks current model to remove all obsolete nodes for the considering that the given changes are obsolete.
   * <p/>
   * Example:
   * <pre>
   * <ol>
   *   <li>There is a particular intellij-local library (change from the gradle project structure);</li>
   *   <li>Corresponding node is shown at the current UI;</li>
   *   <li>The library is removed, i.e. corresponding change has become obsolete;</li>
   *   <li>This method is notified within the obsolete change and is expected to remove the corresponding node;</li>
   * </ol>
   * </pre>
   */
  public void pruneObsoleteNodes(Collection<GradleProjectStructureChange> changes) {
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
          // We need to remove the corresponding node then.
          String moduleName;
          Object library;
          final GradleLibraryDependency gradleEntity = change.getGradleEntity();
          final LibraryOrderEntry intellijEntity = change.getIntellijEntity();
          assert gradleEntity != null || intellijEntity != null;
          if (gradleEntity == null) {
            moduleName = intellijEntity.getOwnerModule().getName();
            library = intellijEntity;
          }
          else {
            moduleName = gradleEntity.getOwnerModule().getName();
            library = gradleEntity;
          }
          final DefaultMutableTreeNode holder = myModuleDependencies.get(moduleName);
          if (holder == null) {
            return;
          }
          for (DefaultMutableTreeNode node = holder.getFirstLeaf(); node != null; node = node.getNextSibling()) {
            GradleProjectStructureNodeDescriptor<?> descriptor = (GradleProjectStructureNodeDescriptor<?>)node.getUserObject();
            if (descriptor.getElement().equals(library)) {
              removeNode(node);
              return;
            }
          }
        }
      });
    }
  }

  private void removeNode(@NotNull TreeNode node) {
    final MutableTreeNode parent = (MutableTreeNode)node.getParent();
    if (parent == null) {
      return;
    }
    int i = parent.getIndex(node);
    if (i <= 0) {
      assert false : node;
      return;
    }
    parent.remove(i);
    myIndexHolder[0] = i;
    myNodeHolder[0] = node;
    nodesWereRemoved(parent, myIndexHolder, myNodeHolder);
  }
}
