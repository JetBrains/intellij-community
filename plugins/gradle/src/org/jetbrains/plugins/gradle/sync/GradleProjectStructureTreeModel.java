package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.hash.HashMap;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.config.PlatformFacade;
import org.jetbrains.plugins.gradle.diff.GradleAbstractConflictingPropertyChange;
import org.jetbrains.plugins.gradle.diff.GradleAbstractEntityPresenceChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.diff.contentroot.GradleContentRootPresenceChange;
import org.jetbrains.plugins.gradle.diff.dependency.GradleDependencyExportedChange;
import org.jetbrains.plugins.gradle.diff.dependency.GradleDependencyScopeChange;
import org.jetbrains.plugins.gradle.diff.dependency.GradleLibraryDependencyPresenceChange;
import org.jetbrains.plugins.gradle.diff.dependency.GradleModuleDependencyPresenceChange;
import org.jetbrains.plugins.gradle.diff.library.GradleJarPresenceChange;
import org.jetbrains.plugins.gradle.diff.module.GradleModulePresenceChange;
import org.jetbrains.plugins.gradle.diff.project.GradleLanguageLevelChange;
import org.jetbrains.plugins.gradle.diff.project.GradleProjectRenameChange;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary;
import org.jetbrains.plugins.gradle.model.gradle.GradleModule;
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType;
import org.jetbrains.plugins.gradle.model.id.*;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeComparator;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeFilter;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import java.util.*;

/**
 * Model for the target project structure tree used by the gradle integration.
 * <p/>
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/30/12 4:20 PM
 */
public class GradleProjectStructureTreeModel extends DefaultTreeModel {
  
  private static final Function<GradleEntityId, GradleEntityId> SELF_MAPPER = new Function.Self<GradleEntityId, GradleEntityId>();

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
  private final Map<String, GradleProjectStructureNode<GradleSyntheticId>> myModuleDependencies
    = new HashMap<String, GradleProjectStructureNode<GradleSyntheticId>>();
  private final Map<String, GradleProjectStructureNode<GradleModuleId>> myModules
    = new HashMap<String, GradleProjectStructureNode<GradleModuleId>>();

  private final Set<GradleProjectStructureNodeFilter> myFilters                   = new HashSet<GradleProjectStructureNodeFilter>();
  private final TreeNode[]                            myNodeHolder                = new TreeNode[1];
  private final int[]                                 myIndexHolder               = new int[1];
  private final NodeListener                          myNodeListener              = new NodeListener();
  private final ObsoleteChangesDispatcher             myObsoleteChangesDispatcher = new ObsoleteChangesDispatcher();
  private final NewChangesDispatcher                  myNewChangesDispatcher      = new NewChangesDispatcher();

  @NotNull private final Project                                   myProject;
  @NotNull private final PlatformFacade                            myPlatformFacade;
  @NotNull private final GradleProjectStructureHelper              myProjectStructureHelper;
  @NotNull private final Comparator<GradleProjectStructureNode<?>> myNodeComparator;
  @NotNull private final GradleProjectStructureChangesModel        myChangesModel;
  
  private Comparator<GradleProjectStructureChange> myChangesComparator;
  private boolean myProcessChangesAtTheSameThread;

  @SuppressWarnings("UnusedDeclaration") // Used implicitly by IoC
  public GradleProjectStructureTreeModel(@NotNull Project project, @NotNull GradleProjectStructureContext context) {
    this(project, context, true);
  }
  
  GradleProjectStructureTreeModel(@NotNull Project project, @NotNull GradleProjectStructureContext context, boolean rebuild) {
    super(null);
    myProject = project;
    myPlatformFacade = context.getPlatformFacade();
    myProjectStructureHelper = context.getProjectStructureHelper();
    myChangesModel = context.getChangesModel();
    myNodeComparator = new GradleProjectStructureNodeComparator(context);

    context.getChangesModel().addListener(new GradleProjectStructureChangeListener() {
      @Override
      public void onChanges(@NotNull final Collection<GradleProjectStructureChange> oldChanges,
                            @NotNull final Collection<GradleProjectStructureChange> currentChanges)
      {
        final Runnable task = new Runnable() {
          @Override
          public void run() {
            List<GradleProjectStructureChange> currentChangesToUse = ContainerUtilRt.newArrayList(currentChanges);
            Collection<GradleProjectStructureChange> obsoleteChangesToUse = ContainerUtil.subtract(oldChanges, currentChanges);
            if (myChangesComparator != null) {
              List<GradleProjectStructureChange> toSort = ContainerUtilRt.newArrayList(obsoleteChangesToUse);
              Collections.sort(toSort, myChangesComparator);
              obsoleteChangesToUse = toSort;
              Collections.sort(currentChangesToUse, myChangesComparator);
            }
            processObsoleteChanges(obsoleteChangesToUse);
            processCurrentChanges(currentChangesToUse);
          }
        };
        if (myProcessChangesAtTheSameThread) {
          task.run();
        }
        else {
          UIUtil.invokeLaterIfNeeded(task);
        }
      }
    });

    if (rebuild) {
      rebuild();
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public GradleProjectStructureNode<GradleProjectId> getRoot() {
    return (GradleProjectStructureNode<GradleProjectId>)super.getRoot();
  }

  public void rebuild() {
    myModuleDependencies.clear();
    myModules.clear();

    GradleProjectId projectId = GradleEntityIdMapper.mapEntityToId(getProject());
    GradleProjectStructureNode<GradleProjectId> root = buildNode(projectId, getProject().getName());
    setRoot(root);
    final Collection<Module> modules = myPlatformFacade.getModules(getProject());
    final List<GradleProjectStructureNode<?>> dependencies = ContainerUtilRt.newArrayList();
    final List<Pair<GradleProjectStructureNode<GradleLibraryDependencyId>, Library>> libraryDependencies = ContainerUtilRt.newArrayList();
    RootPolicy<Object> visitor = new RootPolicy<Object>() {
      @Override
      public Object visitModuleOrderEntry(ModuleOrderEntry moduleOrderEntry, Object value) {
        GradleModuleDependencyId id = GradleEntityIdMapper.mapEntityToId(moduleOrderEntry);
        dependencies.add(buildNode(id, moduleOrderEntry.getModuleName()));
        return value;
      }

      @Override
      public Object visitLibraryOrderEntry(LibraryOrderEntry libraryOrderEntry, Object value) {
        if (libraryOrderEntry.getLibraryName() == null) {
          return value;
        }
        GradleLibraryDependencyId id = GradleEntityIdMapper.mapEntityToId(libraryOrderEntry);
        GradleProjectStructureNode<GradleLibraryDependencyId> dependencyNode = buildNode(id, id.getDependencyName());
        libraryDependencies.add(Pair.create(dependencyNode, libraryOrderEntry.getLibrary()));
        dependencies.add(dependencyNode);
        return value;
      }
    };
    for (Module module : modules) {
      dependencies.clear();
      libraryDependencies.clear();
      final GradleModuleId moduleId = GradleEntityIdMapper.mapEntityToId(module);
      final GradleProjectStructureNode<GradleModuleId> moduleNode = buildNode(moduleId, moduleId.getModuleName());
      myModules.put(module.getName(), moduleNode); // Assuming that module names are unique.
      root.add(moduleNode);
      
      // Content roots
      final Collection<ModuleAwareContentRoot> contentRoots = myPlatformFacade.getContentRoots(module);
      for (ContentEntry entry : contentRoots) {
        GradleContentRootId contentRootId = GradleEntityIdMapper.mapEntityToId(entry);
        moduleNode.add(buildContentRootNode(contentRootId, contentRoots.size() <= 1));
      }
      
      // Dependencies
      for (OrderEntry orderEntry : myPlatformFacade.getOrderEntries(module)) {
        orderEntry.accept(visitor, null);
      }
      if (dependencies.isEmpty()) {
        continue;
      }
      GradleProjectStructureNode<GradleSyntheticId> dependenciesNode = getDependenciesNode(moduleId);
      for (GradleProjectStructureNode<?> dependency : dependencies) {
        dependenciesNode.add(dependency);
      }
      
      // The general idea is to add jar nodes when all tree nodes above have already been initialized.
      if (!libraryDependencies.isEmpty()) {
        for (Pair<GradleProjectStructureNode<GradleLibraryDependencyId>, Library> p : libraryDependencies) {
          populateLibraryDependencyNode(p.first, p.second);
        }
      }
    }
    processCurrentChanges(myChangesModel.getChanges());
    filterNodes(root);
  }

  private void populateLibraryDependencyNode(@NotNull GradleProjectStructureNode<GradleLibraryDependencyId> node,
                                             @Nullable Library library)
  {
    if (library == null) {
      return;
    }

    GradleLibraryId libraryId = node.getDescriptor().getElement().getLibraryId();
    for (VirtualFile file : library.getFiles(OrderRootType.CLASSES)) {
      GradleJarId jarId = new GradleJarId(GradleUtil.getLocalFileSystemPath(file), libraryId);
      GradleProjectStructureNode<GradleJarId> jarNode = buildNode(jarId, GradleUtil.extractNameFromPath(jarId.getPath()));
      jarNode.getDescriptor().setToolTip(jarId.getPath());
      node.add(jarNode);
    }
  }

  @TestOnly
  public void setProcessChangesAtTheSameThread(boolean processChangesAtTheSameThread) {
    myProcessChangesAtTheSameThread = processChangesAtTheSameThread;
  }

  @TestOnly
  public void setChangesComparator(@Nullable Comparator<GradleProjectStructureChange> changesComparator) {
    myChangesComparator = changesComparator;
  }

  private void filterNodes(@NotNull GradleProjectStructureNode<?> root) {
    if (myFilters.isEmpty()) {
      return;
    }
    Deque<GradleProjectStructureNode<?>> toRemove = new ArrayDeque<GradleProjectStructureNode<?>>();
    Stack<GradleProjectStructureNode<?>> toProcess = new Stack<GradleProjectStructureNode<?>>();
    toProcess.push(root);
    while (!toProcess.isEmpty()) {
      final GradleProjectStructureNode<?> current = toProcess.pop();
      toRemove.add(current);
      if (passFilters(current)) {
        toRemove.remove(current);
        // Keep all nodes up to the hierarchy.
        for (GradleProjectStructureNode<?> parent = current.getParent(); parent != null; parent = parent.getParent()) {
          if (!toRemove.remove(parent)) {
            break;
          }
        }
      }
      for (GradleProjectStructureNode<?> child : current) {
        toProcess.push(child);
      }
    }
    for (GradleProjectStructureNode<?> node = toRemove.pollLast(); node != null; node = toRemove.pollLast()) {
      final GradleProjectStructureNode<?> parent = node.getParent();
      if (parent == null) {
        continue;
      }
      parent.remove(node);
      
      // Clear caches.
      final GradleEntityId id = node.getDescriptor().getElement();
      if (id instanceof GradleModuleId) {
        String moduleName = ((GradleModuleId)id).getModuleName();
        myModules.remove(moduleName);
        myModuleDependencies.remove(moduleName);
      }
    }
  }

  public void addFilter(@NotNull GradleProjectStructureNodeFilter filter) {
    myFilters.add(filter);
    rebuild();
  }

  public boolean hasFilter(@NotNull GradleProjectStructureNodeFilter filter) {
    return myFilters.contains(filter);
  }

  public boolean hasAnyFilter() {
    return !myFilters.isEmpty();
  }
  
  public void removeFilter(@NotNull GradleProjectStructureNodeFilter filter) {
    myFilters.remove(filter);
    rebuild();
  }

  public void removeAllFilters() {
    myFilters.clear();
    rebuild();
  }

  /**
   * @param node    node to check
   * @return        <code>true</code> if active filters allow to show given node; <code>false</code> otherwise
   */
  private boolean passFilters(@NotNull GradleProjectStructureNode<?> node) {
    if (myFilters.isEmpty()) {
      return true;
    }
    for (GradleProjectStructureNodeFilter filter : myFilters) {
      if (filter.isVisible(node)) {
        return true;
      }
    }
    return false;
  }
  
  @NotNull
  private static String getContentRootNodeName(@NotNull GradleContentRootId id, boolean singleRoot) {
    final String name = GradleBundle.message("gradle.import.structure.tree.node.content.root");
    if (singleRoot) {
      return name;
    }
    final String path = id.getRootPath();
    final int i = path.lastIndexOf('/');
    if (i < 0) {
      return name;
    }
    return name + ":" + path.substring(i + 1);
  }
  
  @NotNull
  public Project getProject() {
    return myProject;
  }

  @NotNull
  private GradleProjectStructureNode<GradleContentRootId> buildContentRootNode(@NotNull GradleContentRootId id) {
    final boolean singleRoot;
    if (id.getOwner() == GradleEntityOwner.GRADLE) {
      final GradleModule module = myProjectStructureHelper.findGradleModule(id.getModuleName());
      singleRoot = module == null || module.getContentRoots().size() <= 1;
    }
    else {
      final Module module = myProjectStructureHelper.findIntellijModule(id.getModuleName());
      singleRoot = module == null || myPlatformFacade.getContentRoots(module).size() <= 1;
    }
    return buildContentRootNode(id, singleRoot);
  }
  
  @NotNull
  private GradleProjectStructureNode<GradleContentRootId> buildContentRootNode(@NotNull GradleContentRootId id, boolean singleRoot) {
    GradleProjectStructureNode<GradleContentRootId> result = buildNode(id, getContentRootNodeName(id, singleRoot));
    result.getDescriptor().setToolTip(id.getRootPath());
    return result;
  }

  @NotNull
  private <T extends GradleEntityId> GradleProjectStructureNode<T> buildNode(@NotNull T id, @NotNull String name) {
    final GradleProjectStructureNode<T> result = new GradleProjectStructureNode<T>(GradleUtil.buildDescriptor(id, name), myNodeComparator);
    result.addListener(myNodeListener);
    return result;
  }

  @NotNull
  private GradleProjectStructureNode<GradleSyntheticId> getDependenciesNode(@NotNull GradleModuleId id) {
    final GradleProjectStructureNode<GradleSyntheticId> cached = myModuleDependencies.get(id.getModuleName());
    if (cached != null) {
      return cached;
    }
    GradleProjectStructureNode<GradleModuleId> moduleNode = getModuleNode(id);
    GradleProjectStructureNode<GradleSyntheticId> result
      = new GradleProjectStructureNode<GradleSyntheticId>(GradleConstants.DEPENDENCIES_NODE_DESCRIPTOR, myNodeComparator);
    result.addListener(myNodeListener);
    moduleNode.add(result);
    myModuleDependencies.put(id.getModuleName(), result);
    
    return result;
  }
  
  @NotNull
  private GradleProjectStructureNode<GradleModuleId> getModuleNode(@NotNull GradleModuleId id) {
    GradleProjectStructureNode<GradleModuleId> moduleNode = myModules.get(id.getModuleName());
    if (moduleNode == null) {
      moduleNode = buildNode(id, id.getModuleName());
      myModules.put(id.getModuleName(), moduleNode);
      ((GradleProjectStructureNode<?>)root).add(moduleNode);
    }
    return moduleNode;
  }

  /**
   * Notifies current model that particular module roots change just has happened.
   * <p/>
   * The model is expected to update itself if necessary.
   */
  public void onModuleRootsChange() {
    for (GradleProjectStructureNode<GradleSyntheticId> node : myModuleDependencies.values()) {
      node.sortChildren();
    }
  }

  private void removeModuleNodeIfEmpty(@NotNull GradleProjectStructureNode<GradleModuleId> node) {
    if (node.getChildCount() != 0) {
      return;
    }
    node.removeFromParent();
    myModules.remove(node.getDescriptor().getElement().getModuleName());
  }

  private void removeModuleDependencyNodeIfEmpty(@NotNull GradleProjectStructureNode<GradleSyntheticId> node,
                                                 @NotNull GradleModuleId moduleId)
  {
    if (node.getChildCount() != 0) {
      return;
    }
    node.removeFromParent();
    myModuleDependencies.remove(moduleId.getModuleName());
  }
  
  /**
   * Asks current model to update its state in accordance with the given changes.
   * 
   * @param changes  collections that contains all changes between the current gradle and intellij project structures
   */
  public void processCurrentChanges(@NotNull Collection<GradleProjectStructureChange> changes) {
    for (GradleProjectStructureChange change : changes) {
      change.invite(myNewChangesDispatcher);
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
      change.invite(myObsoleteChangesDispatcher);
    }
  }

  private void processNewProjectRenameChange(@NotNull GradleProjectRenameChange change) {
    getRoot().addConflictChange(change);
  }

  private void processNewLanguageLevelChange(@NotNull GradleLanguageLevelChange change) {
    getRoot().addConflictChange(change);
  }

  private void processNewDependencyScopeChange(@NotNull GradleDependencyScopeChange change) {
    processDependencyConflictChange(change, SELF_MAPPER, false);
  }

  private void processNewDependencyExportedChange(@NotNull GradleDependencyExportedChange change) {
    processDependencyConflictChange(change, SELF_MAPPER, false);
  }
  
  private void processNewLibraryDependencyPresenceChange(@NotNull GradleLibraryDependencyPresenceChange change) {
    GradleProjectStructureNode<GradleLibraryDependencyId> dependencyNode = processNewDependencyPresenceChange(change);
    GradleLibraryDependencyId id = change.getGradleEntity();
    if (dependencyNode != null && id != null) {
      GradleLibrary library = myProjectStructureHelper.findGradleLibrary(id.getLibraryId());
      if (library != null) {
        GradleLibraryId libraryId = dependencyNode.getDescriptor().getElement().getLibraryId();
        for (String path : library.getPaths(LibraryPathType.BINARY)) {
          GradleJarId jarId = new GradleJarId(path, libraryId);
          GradleProjectStructureNode<GradleJarId> jarNode = buildNode(jarId, GradleUtil.extractNameFromPath(jarId.getPath()));
          jarNode.setAttributes(GradleTextAttributes.GRADLE_LOCAL_CHANGE);
          jarNode.getDescriptor().setToolTip(jarId.getPath());
          dependencyNode.add(jarNode);
        }
      }
    }
  }

  private void processJarPresenceChange(@NotNull GradleJarPresenceChange change, boolean obsolete) {
    GradleJarId jarId = change.getGradleEntity();
    TextAttributesKey attributes = GradleTextAttributes.GRADLE_LOCAL_CHANGE;
    if (jarId == null) {
      jarId = change.getIntellijEntity();
      attributes = GradleTextAttributes.INTELLIJ_LOCAL_CHANGE;
      assert jarId != null;
    }
    if (obsolete) {
      attributes = GradleTextAttributes.NO_CHANGE;
    }

    for (Map.Entry<String, GradleProjectStructureNode<GradleSyntheticId>> entry : myModuleDependencies.entrySet()) {
      Collection<GradleProjectStructureNode<GradleLibraryDependencyId>> libraryDependencies
        = entry.getValue().getChildren(GradleLibraryDependencyId.class);

      insideModule:
      for (GradleProjectStructureNode<GradleLibraryDependencyId> libraryDependencyNode : libraryDependencies) {
        if (!libraryDependencyNode.getDescriptor().getElement().getLibraryId().equals(jarId.getLibraryId())) {
          continue;
        }

        for (GradleProjectStructureNode<GradleJarId> jarNode : libraryDependencyNode.getChildren(GradleJarId.class)) {
          if (jarNode.getDescriptor().getElement().equals(jarId)) {
            if (obsolete && myProjectStructureHelper.findIntellijJar(jarId) == null) {
              // It was a gradle-local change which is now obsolete. Remove the jar node then.
              jarNode.removeFromParent();
            }
            else {
              jarNode.setAttributes(attributes);
            }
            break insideModule;
          }
        }

        if (obsolete) {
          continue;
        }

        // There is a possible case that both gradle and intellij have a library with the same name but different jar sets.
        // We don't want to show intellij-local jars for the gradle-local module which uses that library then.
        if (jarId.getOwner() ==  GradleEntityOwner.INTELLIJ
            && myModules.get(entry.getKey()).getDescriptor().getAttributes() == GradleTextAttributes.GRADLE_LOCAL_CHANGE)
        {
          continue;
        }

          // When control flow reaches this place that means that this is a new jar attached to a library. Hence, we need to add a node.
        GradleProjectStructureNode<GradleJarId> newNode = buildNode(jarId, GradleUtil.extractNameFromPath(jarId.getPath()));
        newNode.setAttributes(attributes);
        newNode.getDescriptor().setToolTip(jarId.getPath());
        if (passFilters(newNode)) {
          libraryDependencyNode.add(newNode);
        }
      }
    }
  }
  
  private void processNewModuleDependencyPresenceChange(@NotNull GradleModuleDependencyPresenceChange change) {
    processNewDependencyPresenceChange(change);
  }
  
  @SuppressWarnings("unchecked")
  @Nullable
  private <I extends GradleAbstractDependencyId> GradleProjectStructureNode<I> processNewDependencyPresenceChange(
    @NotNull GradleAbstractEntityPresenceChange<I> change)
  {
    I id = change.getGradleEntity();
    TextAttributesKey attributes = GradleTextAttributes.GRADLE_LOCAL_CHANGE;
    if (id == null) {
      id = change.getIntellijEntity();
      attributes = GradleTextAttributes.INTELLIJ_LOCAL_CHANGE;
    }
    assert id != null;
    final GradleProjectStructureNode<GradleSyntheticId> dependenciesNode = getDependenciesNode(id.getOwnerModuleId());
    Class<I> clazz = (Class<I>)id.getClass();
    for (GradleProjectStructureNode<I> node : dependenciesNode.getChildren(clazz)) {
      if (id.equals(node.getDescriptor().getElement())) {
        node.setAttributes(attributes);
        return node;
      }
    }
    GradleProjectStructureNode<I> newNode = buildNode(id, id.getDependencyName());
    dependenciesNode.add(newNode);
    newNode.setAttributes(attributes);

    if (passFilters(newNode)) {
      return newNode;
    }

    newNode.removeFromParent();
    removeModuleDependencyNodeIfEmpty(dependenciesNode, id.getOwnerModuleId());
    removeModuleNodeIfEmpty(getModuleNode(id.getOwnerModuleId()));
    return null;
  }

  private void processNewModulePresenceChange(@NotNull GradleModulePresenceChange change) {
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
    moduleNode.setAttributes(key);
    
    if (!passFilters(moduleNode)) {
      removeModuleNodeIfEmpty(moduleNode);
    }
  }

  private void processNewContentRootPresenceChange(@NotNull GradleContentRootPresenceChange change) {
    GradleContentRootId id = change.getGradleEntity();
    TextAttributesKey key = GradleTextAttributes.GRADLE_LOCAL_CHANGE;
    if (id == null) {
      id = change.getIntellijEntity();
      key = GradleTextAttributes.INTELLIJ_LOCAL_CHANGE;
    }
    assert id != null;
    final GradleProjectStructureNode<GradleModuleId> moduleNode = getModuleNode(id.getModuleId());
    for (GradleProjectStructureNode<GradleContentRootId> contentRoot : moduleNode.getChildren(GradleContentRootId.class)) {
      if (id.equals(contentRoot.getDescriptor().getElement())) {
        contentRoot.setAttributes(key);
        return;
      }
    }
    GradleProjectStructureNode<GradleContentRootId> contentRootNode = buildContentRootNode(id);
    moduleNode.add(contentRootNode);
    contentRootNode.setAttributes(key);

    if (!passFilters(contentRootNode)) {
      contentRootNode.removeFromParent();
      removeModuleNodeIfEmpty(moduleNode);
    }
  }
  
  private void processObsoleteProjectRenameChange(@NotNull GradleProjectRenameChange change) {
    getRoot().removeConflictChange(change);
  }
  
  private void processObsoleteLanguageLevelChange(@NotNull GradleLanguageLevelChange change) {
    getRoot().removeConflictChange(change);
  }
  
  private void processObsoleteDependencyScopeChange(@NotNull GradleDependencyScopeChange change) {
    processDependencyConflictChange(change, SELF_MAPPER, true);
  }

  private void processObsoleteDependencyExportedChange(@NotNull GradleDependencyExportedChange change) {
    processDependencyConflictChange(change, SELF_MAPPER, true);
  }
  
  private void processObsoleteLibraryDependencyPresenceChange(@NotNull GradleLibraryDependencyPresenceChange change) {
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
    processObsoleteDependencyPresenceChange(id, removeNode);
  }

  private void processObsoleteModuleDependencyPresenceChange(@NotNull GradleModuleDependencyPresenceChange change) {
    GradleModuleDependencyId id = change.getGradleEntity();
    boolean removeNode;
    if (id == null) {
      id = change.getIntellijEntity();
      assert id != null;
      removeNode = !myProjectStructureHelper.isIntellijModuleDependencyExist(id);
    }
    else {
      removeNode = !myProjectStructureHelper.isGradleModuleDependencyExist(id);
    }
    processObsoleteDependencyPresenceChange(id, removeNode);
  }

  private void processObsoleteDependencyPresenceChange(@NotNull GradleAbstractDependencyId id, boolean removeNode) {
    final GradleProjectStructureNode<GradleSyntheticId> holder = myModuleDependencies.get(id.getOwnerModuleName());
    if (holder == null) {
      return;
    }

    // There are two possible cases why 'local library dependency' change is obsolete:
    //   1. Corresponding dependency has been added at the counterparty;
    //   2. The 'local dependency' has been removed;
    // We should distinguish between those situations because we need to mark the node as 'synced' at one case and
    // completely removed at another one.

    for (GradleProjectStructureNode<? extends GradleAbstractDependencyId> node : holder.getChildren(id.getClass())) {
      GradleProjectStructureNodeDescriptor<? extends GradleAbstractDependencyId> descriptor = node.getDescriptor();
      if (!id.equals(descriptor.getElement())) {
        continue;
      }
      if (removeNode) {
        holder.remove(node);
      }
      else {
        descriptor.setAttributes(GradleTextAttributes.NO_CHANGE);
        holder.correctChildPositionIfNecessary(node);
        if (!passFilters(node)) {
          node.removeFromParent();
          removeModuleDependencyNodeIfEmpty(holder, id.getOwnerModuleId());
          removeModuleNodeIfEmpty(myModules.get(id.getOwnerModuleName()));
        }
      }
      return;
    }
  }

  private void processObsoleteModulePresenceChange(@NotNull GradleModulePresenceChange change) {
    GradleModuleId id = change.getGradleEntity();
    boolean removeNode;
    if (id == null) {
      id = change.getIntellijEntity();
      assert id != null;
      removeNode = myProjectStructureHelper.findIntellijModule(id.getModuleName()) == null;
    }
    else {
      removeNode = myProjectStructureHelper.findGradleModule(id.getModuleName()) == null;
    }
    

    // There are two possible cases why 'module presence' change is obsolete:
    //   1. Corresponding module has been added at the counterparty;
    //   2. The 'local module' has been removed;
    // We should distinguish between those situations because we need to mark the node as 'synced' at one case and
    // completely removed at another one.
    
    final GradleProjectStructureNode<GradleModuleId> moduleNode = myModules.get(id.getModuleName());
    if (moduleNode == null) {
      return;
    }
    if (removeNode) {
      moduleNode.removeFromParent();
    }
    else {
      moduleNode.setAttributes(GradleTextAttributes.NO_CHANGE);
      if (!passFilters(moduleNode)) {
        removeModuleNodeIfEmpty(moduleNode);
      }
    }
  }

  private void processObsoleteContentRootPresenceChange(@NotNull GradleContentRootPresenceChange change) {
    GradleContentRootId id = change.getGradleEntity();
    final boolean removeNode;
    if (id == null) {
      id = change.getIntellijEntity();
      assert id != null;
      removeNode = myProjectStructureHelper.findIntellijContentRoot(id) == null;
    }
    else {
      removeNode = myProjectStructureHelper.findGradleContentRoot(id) == null;
    }
    final GradleProjectStructureNode<GradleModuleId> moduleNode = myModules.get(id.getModuleName());
    if (moduleNode == null) {
      return;
    }
    for (GradleProjectStructureNode<GradleContentRootId> contentRootNode : moduleNode.getChildren(GradleContentRootId.class)) {
      if (!id.equals(contentRootNode.getDescriptor().getElement())) {
        continue;
      }
      if (removeNode) {
        contentRootNode.removeFromParent();
      }
      else {
        contentRootNode.setAttributes(GradleTextAttributes.NO_CHANGE);
        if (!passFilters(contentRootNode)) {
          contentRootNode.removeFromParent();
          removeModuleNodeIfEmpty(moduleNode);
        }
      }
      return;
    }
  }

  private void processDependencyConflictChange(@NotNull GradleAbstractConflictingPropertyChange<?> change,
                                               @NotNull Function<GradleEntityId, GradleEntityId> nodeIdMapper,
                                               boolean obsolete)
  {
    for (Map.Entry<String, GradleProjectStructureNode<GradleSyntheticId>> entry : myModuleDependencies.entrySet()) {
      for (GradleProjectStructureNode<?> dependencyNode : entry.getValue()) {
        if (!change.getEntityId().equals(nodeIdMapper.fun(dependencyNode.getDescriptor().getElement()))) {
          continue;
        }
        if (obsolete) {
          dependencyNode.removeConflictChange(change);
        }
        else {
          dependencyNode.addConflictChange(change);
        }
        if (!passFilters(dependencyNode)) {
          dependencyNode.removeFromParent();
          final GradleProjectStructureNode<GradleModuleId> moduleNode = myModules.get(entry.getKey());
          final GradleModuleId moduleId = moduleNode.getDescriptor().getElement();
          removeModuleDependencyNodeIfEmpty(entry.getValue(), moduleId);
          removeModuleNodeIfEmpty(moduleNode);
        }
        break;
      }
    }
  }

  private class NodeListener implements GradleProjectStructureNode.Listener {
    
    @Override
    public void onNodeAdded(@NotNull GradleProjectStructureNode<?> node, int index) {
      myIndexHolder[0] = index;
      nodesWereInserted(node.getParent(), myIndexHolder);
    }

    @Override
    public void onNodeRemoved(@NotNull GradleProjectStructureNode<?> parent,
                              @NotNull GradleProjectStructureNode<?> removedChild,
                              int removedChildIndex)
    {
      myIndexHolder[0] = removedChildIndex;
      myNodeHolder[0] = removedChild;
      nodesWereRemoved(parent, myIndexHolder, myNodeHolder); 
    }

    @Override
    public void onNodeChanged(@NotNull GradleProjectStructureNode<?> node) {
      nodeChanged(node);
    }

    @Override
    public void onNodeChildrenChanged(@NotNull GradleProjectStructureNode<?> parent, int[] childIndices) {
      nodesChanged(parent, childIndices);
    }
  }
  
  private class NewChangesDispatcher implements GradleProjectStructureChangeVisitor {
    @Override public void visit(@NotNull GradleProjectRenameChange change) { processNewProjectRenameChange(change); }
    @Override public void visit(@NotNull GradleLanguageLevelChange change) { processNewLanguageLevelChange(change); }
    @Override public void visit(@NotNull GradleModulePresenceChange change) { processNewModulePresenceChange(change); }
    @Override public void visit(@NotNull GradleContentRootPresenceChange change) { processNewContentRootPresenceChange(change); }
    @Override public void visit(@NotNull GradleLibraryDependencyPresenceChange change) { processNewLibraryDependencyPresenceChange(change); }
    @Override public void visit(@NotNull GradleJarPresenceChange change) { processJarPresenceChange(change, false); }
    @Override public void visit(@NotNull GradleModuleDependencyPresenceChange change) { processNewModuleDependencyPresenceChange(change); }
    @Override public void visit(@NotNull GradleDependencyScopeChange change) { processNewDependencyScopeChange(change); }
    @Override public void visit(@NotNull GradleDependencyExportedChange change) { processNewDependencyExportedChange(change);
    }
  }
  
  private class ObsoleteChangesDispatcher implements GradleProjectStructureChangeVisitor {
    @Override public void visit(@NotNull GradleProjectRenameChange change) { processObsoleteProjectRenameChange(change); }
    @Override public void visit(@NotNull GradleLanguageLevelChange change) { processObsoleteLanguageLevelChange(change); }
    @Override public void visit(@NotNull GradleModulePresenceChange change) { processObsoleteModulePresenceChange(change); }
    @Override public void visit(@NotNull GradleContentRootPresenceChange change) { processObsoleteContentRootPresenceChange(change); }
    @Override public void visit(@NotNull GradleLibraryDependencyPresenceChange change) {
      processObsoleteLibraryDependencyPresenceChange(change); 
    }
    @Override public void visit(@NotNull GradleJarPresenceChange change) { processJarPresenceChange(change, true); }
    @Override public void visit(@NotNull GradleModuleDependencyPresenceChange change) {
      processObsoleteModuleDependencyPresenceChange(change); 
    }
    @Override public void visit(@NotNull GradleDependencyScopeChange change) { processObsoleteDependencyScopeChange(change); }
    @Override public void visit(@NotNull GradleDependencyExportedChange change) { processObsoleteDependencyExportedChange(change); }
  }
}
