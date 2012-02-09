package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.diff.*;
import org.jetbrains.plugins.gradle.model.GradleEntityType;
import org.jetbrains.plugins.gradle.model.GradleLibraryDependencyId;
import org.jetbrains.plugins.gradle.ui.GradleIcons;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
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
  private final Map<String, GradleProjectStructureNode<String>> myModuleDependencies
    = new HashMap<String, GradleProjectStructureNode<String>>();
  private final Map<String, GradleProjectStructureNode<String>> myModules
    = new HashMap<String, GradleProjectStructureNode<String>>();

  private final TreeNode[] myNodeHolder  = new TreeNode[1];
  private final int[]      myIndexHolder = new int[1];

  private final Project                      myProject;
  private final PlatformFacade               myPlatformFacade;
  private final GradleProjectStructureHelper myProjectStructureHelper;

  public GradleProjectStructureTreeModel(@NotNull Project project,
                                         @NotNull PlatformFacade platformFacade,
                                         @NotNull GradleProjectStructureHelper projectStructureHelper) {
    super(null);
    myProject = project;
    myPlatformFacade = platformFacade;
    myProjectStructureHelper = projectStructureHelper;
    rebuild();
  }

  public void rebuild() {
    myModuleDependencies.clear();
    myModules.clear();

    GradleProjectStructureNode<Project> root
      = buildNode(getProject(), GradleEntityType.PROJECT, getProject().getName(), myPlatformFacade.getProjectIcon());
    final Collection<Module> modules = myPlatformFacade.getModules(getProject());
    RootPolicy<LibraryOrderEntry> policy = new RootPolicy<LibraryOrderEntry>() {
      @Override
      public LibraryOrderEntry visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, LibraryOrderEntry value) {
        return libraryOrderEntry;
      }
    };
    for (Module module : modules) {
      final GradleProjectStructureNode<String> moduleNode = buildNode(GradleEntityType.MODULE, module.getName(), GradleIcons.MODULE_ICON);
      myModules.put(module.getName(), moduleNode); // Assuming that module names are unique.
      List<LibraryOrderEntry> libraryDependencies = new ArrayList<LibraryOrderEntry>();
      for (OrderEntry orderEntry : myPlatformFacade.getOrderEntries(module)) {
        final LibraryOrderEntry libraryDependency = orderEntry.accept(policy, null);
        libraryDependencies.add(libraryDependency);
      }
      if (!libraryDependencies.isEmpty()) {
        GradleProjectStructureNode<String> dependenciesNode = getDependenciesNode(module.getName());
        for (LibraryOrderEntry dependency : libraryDependencies) {
          GradleLibraryDependencyId id = GradleLibraryDependencyId.of(dependency);
          if (id == null) {
            continue;
          }
          dependenciesNode.add(buildNode(id, GradleEntityType.LIBRARY_DEPENDENCY, id.getLibraryName(), GradleIcons.LIB_ICON));
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
  
  private static <T> GradleProjectStructureNodeDescriptor<T> buildDescriptor(@NotNull T entity, @NotNull String name, @NotNull Icon icon) {
    return new GradleProjectStructureNodeDescriptor<T>(entity, name, icon);
  }

  private static GradleProjectStructureNode<String> buildNode(@NotNull GradleEntityType type, @NotNull String name, @NotNull Icon icon) {
    return buildNode(name, type, name, icon);
  }

  private static <T> GradleProjectStructureNode<T> buildNode(@NotNull T entity,
                                                             @NotNull GradleEntityType type,
                                                             @NotNull String name,
                                                             @NotNull Icon icon)
  {
    return new GradleProjectStructureNode<T>(buildDescriptor(entity, name, icon), type);
  }

  private GradleProjectStructureNode<String> getDependenciesNode(@NotNull String moduleName) {
    final GradleProjectStructureNode<String> cached = myModuleDependencies.get(moduleName);
    if (cached != null) {
      return cached;
    }
    GradleProjectStructureNode<String> moduleNode = myModules.get(moduleName);
    if (moduleNode == null) {
      moduleNode = buildNode(GradleEntityType.MODULE, moduleName, GradleIcons.MODULE_ICON);
      myModules.put(moduleName, moduleNode);
    }

    GradleProjectStructureNode<String> result = new GradleProjectStructureNode<String>(GradleConstants.DEPENDENCIES_NODE_DESCRIPTOR, GradleEntityType.SYNTHETIC);
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
      change.invite(new GradleProjectStructureChangeVisitorAdapter() {
        @Override
        public void visit(@NotNull GradleMismatchedLibraryPathChange change) {
          for (GradleProjectStructureNode<String> holder : myModuleDependencies.values()) {
            for (GradleProjectStructureNode<GradleLibraryDependencyId> dependencyNode : holder.getChildren(GradleLibraryDependencyId.class)) {
              final GradleLibraryDependencyId id = dependencyNode.getDescriptor().getElement();
              if (change.getLibraryName().equals(id.getLibraryName())) {
                dependencyNode.addConflictChange(change);
                break;
              }
            }
          }
        }

        @Override
        public void visit(@NotNull GradleLibraryDependencyPresenceChange change) {
          GradleLibraryDependencyId id = change.getGradleEntity();
          TextAttributesKey attributes = GradleTextAttributes.GRADLE_LOCAL_CHANGE;
          if (id == null) {
            id = change.getIntellijEntity();
            attributes = GradleTextAttributes.INTELLIJ_LOCAL_CHANGE;
          }
          assert id != null;
          final GradleProjectStructureNode<String> dependenciesNode = getDependenciesNode(id.getModuleName());
          for (GradleProjectStructureNode<GradleLibraryDependencyId> node : dependenciesNode.getChildren(GradleLibraryDependencyId.class)) {
            GradleProjectStructureNodeDescriptor<GradleLibraryDependencyId> d = node.getDescriptor();
            if (id.equals(d.getElement())) {
              d.setAttributes(attributes);
              nodeStructureChanged(node);
              return;
            }
          }
          GradleProjectStructureNode<GradleLibraryDependencyId> newNode
            = buildNode(id, GradleEntityType.LIBRARY_DEPENDENCY, id.getLibraryName(), GradleIcons.LIB_ICON);
          newNode.getDescriptor().setAttributes(attributes);
          dependenciesNode.add(newNode);
          nodeStructureChanged(dependenciesNode);
        }
      });
    }
  }

  /**
   * Asks current model to process given changes assuming that they are obsolete.
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
  public void processObsoleteChanges(Collection<GradleProjectStructureChange> changes) {
    for (GradleProjectStructureChange change : changes) {
      change.invite(new GradleProjectStructureChangeVisitorAdapter() {
        @Override
        public void visit(@NotNull GradleMismatchedLibraryPathChange change) {
          for (GradleProjectStructureNode<String> holder : myModuleDependencies.values()) {
            for (GradleProjectStructureNode<GradleLibraryDependencyId> node : holder.getChildren(GradleLibraryDependencyId.class)) {
              final GradleLibraryDependencyId id = node.getDescriptor().getElement();
              if (id.getLibraryName().equals(change.getLibraryName())) {
                node.removeConflictChange(change);
                nodeChanged(node);
                break;
              }
            }
          }
        }

        @Override
        public void visit(@NotNull GradleLibraryDependencyPresenceChange change) {
          // We need to remove the corresponding node then.
          GradleLibraryDependencyId id = change.getGradleEntity();
          boolean removeNode;
          if (id == null) {
            id = change.getIntellijEntity();
            assert id != null;
            removeNode = !myProjectStructureHelper.isIntellijLibraryDependencyExist(id);
          }
          else {
            removeNode = !myProjectStructureHelper.isGradleLibraryDependencyExist(id);
          }
          final GradleProjectStructureNode<String> holder = myModuleDependencies.get(id.getModuleName());
          if (holder == null) {
            return;
          }
          
          // There are two possible cases why 'local library dependency' change is obsolete:
          //   1. Corresponding dependency has been added at the counterparty;
          //   2. The 'local dependency' has been removed;
          // We should distinguish between those situations because we need to mark the node as 'synced' at one case and
          // completely removed at another one.
          
          for (GradleProjectStructureNode<GradleLibraryDependencyId> node : holder.getChildren(GradleLibraryDependencyId.class)) {
            GradleProjectStructureNodeDescriptor<GradleLibraryDependencyId> descriptor = node.getDescriptor();
            if (!id.equals(descriptor.getElement())) {
              continue;
            }
            if (removeNode) {
              removeNode(node);
            }
            else {
              descriptor.setAttributes(GradleTextAttributes.GRADLE_NO_CHANGE);
            }
            return;
          }
        }
      });
    }
  }

  private void removeNode(@NotNull GradleProjectStructureNode node) {
    final GradleProjectStructureNode parent = node.getParent();
    if (parent == null) {
      return;
    }
    int i = parent.getIndex(node);
    if (i < 0) {
      assert false : node;
      return;
    }
    parent.remove(i);
    myIndexHolder[0] = i;
    myNodeHolder[0] = node;
    nodesWereRemoved(parent, myIndexHolder, myNodeHolder);
  }
}
