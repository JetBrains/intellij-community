package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.RootPolicy;
import com.intellij.util.containers.hash.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.diff.*;
import org.jetbrains.plugins.gradle.model.GradleEntityType;
import org.jetbrains.plugins.gradle.model.id.*;
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
  private final Map<String, GradleProjectStructureNode<GradleModuleId>> myModules
    = new HashMap<String, GradleProjectStructureNode<GradleModuleId>>();

  private final TreeNode[]   myNodeHolder   = new TreeNode[1];
  private final int[]        myIndexHolder  = new int[1];
  private final NodeListener myNodeListener = new NodeListener();

  @NotNull private final Project                      myProject;
  @NotNull private final PlatformFacade               myPlatformFacade;
  @NotNull private final GradleProjectStructureHelper myProjectStructureHelper;

  public GradleProjectStructureTreeModel(@NotNull Project project,
                                         @NotNull PlatformFacade platformFacade,
                                         @NotNull GradleProjectStructureHelper projectStructureHelper)
  {
    super(null);
    myProject = project;
    myPlatformFacade = platformFacade;
    myProjectStructureHelper = projectStructureHelper;
    rebuild();
  }

  public void rebuild() {
    myModuleDependencies.clear();
    myModules.clear();

    GradleProjectId projectId = GradleEntityIdMapper.mapEntityToId(getProject());
    GradleProjectStructureNode<GradleProjectId> root = buildNode(projectId, getProject().getName(), myPlatformFacade.getProjectIcon());
    final Collection<Module> modules = myPlatformFacade.getModules(getProject());
    final List<GradleProjectStructureNode<?>> dependencies = new ArrayList<GradleProjectStructureNode<?>>();
    RootPolicy<Object> visitor = new RootPolicy<Object>() {
      @Override
      public Object visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, Object value) {
        GradleModuleDependencyId id = GradleEntityIdMapper.mapEntityToId(moduleOrderEntry);
        dependencies.add(buildNode(id, moduleOrderEntry.getModuleName(), GradleIcons.MODULE_ICON));
        return value;
      }

      @Override
      public Object visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, Object value) {
        if (libraryOrderEntry.getLibraryName() == null) {
          return value;
        }
        GradleLibraryDependencyId id = GradleEntityIdMapper.mapEntityToId(libraryOrderEntry);
        dependencies.add(buildNode(id, id.getLibraryName(), GradleIcons.LIB_ICON));
        return value;
      }
    };
    for (Module module : modules) {
      dependencies.clear();
      final GradleModuleId moduleId = GradleEntityIdMapper.mapEntityToId(module);
      final GradleProjectStructureNode<GradleModuleId> moduleNode = buildNode(moduleId, moduleId.getModuleName(), GradleIcons.MODULE_ICON);
      myModules.put(module.getName(), moduleNode); // Assuming that module names are unique.
      root.add(moduleNode);
      for (OrderEntry orderEntry : myPlatformFacade.getOrderEntries(module)) {
        orderEntry.accept(visitor, null);
      }
      if (dependencies.isEmpty()) {
        continue;
      }
      GradleProjectStructureNode<String> dependenciesNode = getDependenciesNode(moduleId);
      for (GradleProjectStructureNode<?> dependency : dependencies) {
        dependenciesNode.add(dependency);
      }
      moduleNode.add(dependenciesNode);
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

  private <T extends GradleEntityId> GradleProjectStructureNode<T> buildNode(@NotNull T entityId,
                                                                                    @NotNull String name,
                                                                                    @NotNull Icon icon)
  {
    final GradleProjectStructureNode<T> result
      = new GradleProjectStructureNode<T>(buildDescriptor(entityId, name, icon), entityId.getType());
    result.addListener(myNodeListener);
    return result;
  }

  private GradleProjectStructureNode<String> getDependenciesNode(@NotNull GradleModuleId id) {
    final GradleProjectStructureNode<String> cached = myModuleDependencies.get(id.getModuleName());
    if (cached != null) {
      return cached;
    }
    GradleProjectStructureNode<GradleModuleId> moduleNode = getModuleNode(id);
    GradleProjectStructureNode<String> result
      = new GradleProjectStructureNode<String>(GradleConstants.DEPENDENCIES_NODE_DESCRIPTOR, GradleEntityType.SYNTHETIC);
    moduleNode.add(result);
    myModuleDependencies.put(id.getModuleName(), result);
    
    return result;
  }
  
  @NotNull
  private GradleProjectStructureNode<GradleModuleId> getModuleNode(@NotNull GradleModuleId id) {
    GradleProjectStructureNode<GradleModuleId> moduleNode = myModules.get(id.getModuleName());
    if (moduleNode == null) {
      moduleNode = buildNode(id, id.getModuleName(), GradleIcons.MODULE_ICON);
      myModules.put(id.getModuleName(), moduleNode);
      ((GradleProjectStructureNode<?>)root).add(moduleNode);
    }
    return moduleNode;
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
        public void visit(@NotNull GradleModulePresenceChange change) {
          final GradleModuleId id;
          final TextAttributesKey key;
          if (change.getGradleEntity() == null) {
            id = change.getIntellijEntity();
            key = GradleTextAttributes.INTELLIJ_LOCAL_CHANGE;
          }
          else {
            id = change.getGradleEntity();
            key = GradleTextAttributes.GRADLE_LOCAL_CHANGE;
          }
          assert id != null;
          final GradleProjectStructureNode<GradleModuleId> moduleNode = getModuleNode(id);
          moduleNode.getDescriptor().setAttributes(key);
          nodeChanged(moduleNode);
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
          final GradleProjectStructureNode<String> dependenciesNode = getDependenciesNode(id.getModuleId());
          for (GradleProjectStructureNode<GradleLibraryDependencyId> node : dependenciesNode.getChildren(GradleLibraryDependencyId.class)) {
            GradleProjectStructureNodeDescriptor<GradleLibraryDependencyId> d = node.getDescriptor();
            if (id.equals(d.getElement())) {
              d.setAttributes(attributes);
              nodeStructureChanged(node);
              return;
            }
          }
          GradleProjectStructureNode<GradleLibraryDependencyId> newNode = buildNode(id, id.getLibraryName(), GradleIcons.LIB_ICON);
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
      change.invite(new GradleProjectStructureChangeVisitor() {
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
              holder.remove(node);
            }
            else {
              descriptor.setAttributes(GradleTextAttributes.GRADLE_NO_CHANGE);
              holder.correctChildPositionIfNecessary(node);
            }
            return;
          }
        }

        @Override
        public void visit(@NotNull GradleProjectRenameChange change) {
          // TODO den implement 
        }

        @Override
        public void visit(@NotNull GradleLanguageLevelChange change) {
          // TODO den implement 
        }

        @Override
        public void visit(@NotNull GradleModulePresenceChange change) {
          GradleModuleId id = change.getGradleEntity();
          if (id == null) {
            id = change.getIntellijEntity();
          }
          assert id != null;
          final GradleProjectStructureNode<GradleModuleId> moduleNode = myModules.get(id.getModuleName());
          if (moduleNode != null) {
            moduleNode.getDescriptor().setAttributes(GradleTextAttributes.GRADLE_NO_CHANGE);
          }
        }
      });
    }
  }
  
  private class NodeListener implements GradleProjectStructureNode.Listener {
    
    @Override
    public void onNodeAdded(@NotNull GradleProjectStructureNode<?> node, int index) {
      myIndexHolder[0] = index;
      nodesWereInserted(node.getParent(), myIndexHolder);
    }

    @Override
    public void onNodeRemoved(@NotNull GradleProjectStructureNode<?> node, int index) {
      myIndexHolder[0] = index;
      myNodeHolder[0] = node;
      nodesWereRemoved(node.getParent(), myIndexHolder, myNodeHolder); 
    }
  }
}
