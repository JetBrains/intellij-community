package org.jetbrains.plugins.gradle.manage;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.config.GradleTextAttributes;
import org.jetbrains.plugins.gradle.model.GradleEntityType;
import org.jetbrains.plugins.gradle.model.gradle.*;
import org.jetbrains.plugins.gradle.model.id.GradleContentRootId;
import org.jetbrains.plugins.gradle.model.id.GradleEntityId;
import org.jetbrains.plugins.gradle.model.id.GradleEntityIdMapper;
import org.jetbrains.plugins.gradle.model.intellij.ModuleAwareContentRoot;
import org.jetbrains.plugins.gradle.sync.GradleProjectStructureHelper;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNode;
import org.jetbrains.plugins.gradle.ui.GradleProjectStructureNodeDescriptor;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.Collection;
import java.util.Set;

/**
 * Gradle integration shows a project structure tree which contain nodes for gradle-local entities (modules, libraries etc).
 * End-user can select interested nodes and import them into the current intellij project.
 * <p/>
 * This class helps during that at the following ways:
 * <pre>
 * <ul>
 *   <li>filters out non gradle-local nodes;</li>
 *   <li>
 *     collects all nodes that should be imported. For example, an user can mark 'module' node to import. We need to import not
 *     only that module but its (transitive) dependencies as well. I.e. basically the algorithm looks like 'import all entities up
 *     to the path to root and all sub-entities';
 *   </li>
 *   <li>
 *     sorts entities to import in topological order. Example: let module<sub>1</sub> depend on module<sub>2</sub>. We need to
 *     import module<sub>2</sub> before module<sub>1</sub> then;
 *   </li>
 * </ul>
 * </pre>
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/10/12 11:51 AM
 */
public class GradleLocalNodeManageHelper {

  @NotNull private final GradleProjectStructureHelper myProjectStructureHelper;
  @NotNull private final GradleEntityIdMapper         myIdMapper;
  @NotNull private final GradleEntityManageHelper     myEntityManageHelper;

  public GradleLocalNodeManageHelper(@NotNull GradleProjectStructureHelper projectStructureHelper,
                                     @NotNull GradleEntityIdMapper idMapper,
                                     @NotNull GradleEntityManageHelper entityManageHelper)
  {
    myProjectStructureHelper = projectStructureHelper;
    myIdMapper = idMapper;
    myEntityManageHelper = entityManageHelper;
  }

  /**
   * {@link #deriveEntitiesToImport(Iterable) Derives} target entities from the given nodes
   * and {@link GradleEntityManageHelper#importEntities(Collection, boolean) imports them}.
   *
   * @param nodes  'anchor nodes' to import
   */
  public void importNodes(Iterable<GradleProjectStructureNode<?>> nodes) {
    final Collection<GradleEntity> entities = deriveEntitiesToImport(nodes);
    myEntityManageHelper.importEntities(entities, true);
  }

  /**
   * Collects all nodes that should be imported based on the given nodes and returns corresponding gradle entities
   * sorted in topological order.
   *
   * @param nodes  'anchor nodes' to import
   * @return collection of gradle entities that should be imported based on the given nodes
   */
  @NotNull
  public Collection<GradleEntity> deriveEntitiesToImport(@NotNull Iterable<GradleProjectStructureNode<?>> nodes) {
    Context context = new Context();
    for (GradleProjectStructureNode<?> node : nodes) {
      collectEntitiesToImport(node, context);
    }
    return context.entities;
  }

  private void collectEntitiesToImport(@NotNull GradleProjectStructureNode<?> node, @NotNull Context context) {
    // Collect up.
    for (GradleProjectStructureNode<?> n = node.getParent(); n != null; n = n.getParent()) {
      final GradleProjectStructureNodeDescriptor<?> descriptor = n.getDescriptor();
      if (n.getDescriptor().getElement().getType() == GradleEntityType.SYNTHETIC) {
        continue;
      }
      if (descriptor.getAttributes() != GradleTextAttributes.GRADLE_LOCAL_CHANGE) {
        break;
      }
      Object id = descriptor.getElement();
      final Object entity = myIdMapper.mapIdToEntity((GradleEntityId)id);
      if (entity instanceof GradleEntity) {
        ((GradleEntity)entity).invite(context.visitor);
      }
    }

    // Collect down.
    final Stack<GradleEntity> toProcess = new Stack<GradleEntity>();
    final Object id = node.getDescriptor().getElement();
    final Object entity = myIdMapper.mapIdToEntity((GradleEntityId)id);
    if (entity instanceof GradleEntity) {
      toProcess.push((GradleEntity)entity);
    }

    context.recursive = true;
    while (!toProcess.isEmpty()) {
      final GradleEntity e = toProcess.pop();
      e.invite(context.visitor);
    }
  }

  private void collectModuleEntities(@NotNull GradleModule module, @NotNull Context context) {
    final Module intellijModule = myProjectStructureHelper.findIdeModule(module);
    if (intellijModule != null) {
      // Already imported
      return;
    }
    context.entities.add(module);
    if (!context.recursive) {
      return;
    }
    for (GradleContentRoot contentRoot : module.getContentRoots()) {
      contentRoot.invite(context.visitor);
    }
    for (GradleDependency dependency : module.getDependencies()) {
      dependency.invite(context.visitor);
    }
  }

  private void collectContentRoots(@NotNull GradleContentRoot contentRoot, @NotNull Context context) {
    final GradleContentRootId id = GradleEntityIdMapper.mapEntityToId(contentRoot);
    final ModuleAwareContentRoot intellijContentRoot = myProjectStructureHelper.findIdeContentRoot(id);
    if (intellijContentRoot != null) {
      // Already imported.
      return;
    }
    context.entities.add(contentRoot);
  }
  
  private void collectModuleDependencyEntities(@NotNull GradleModuleDependency dependency, @NotNull Context context) {
    final ModuleOrderEntry intellijModuleDependency = myProjectStructureHelper.findIdeModuleDependency(dependency);
    if (intellijModuleDependency != null) {
      // Already imported.
      return;
    }
    context.entities.add(dependency);
    final GradleModule gradleModule = dependency.getTarget();
    final Module intellijModule = myProjectStructureHelper.findIdeModule(gradleModule);
    if (intellijModule != null) {
      return;
    }
    boolean r = context.recursive;
    context.recursive = true;
    try {
      gradleModule.invite(context.visitor);
    }
    finally {
      context.recursive = r;
    }
  }

  private void collectLibraryDependencyEntities(@NotNull GradleLibraryDependency dependency, @NotNull Context context) {
    final LibraryOrderEntry intellijDependency
      = myProjectStructureHelper.findIdeLibraryDependency(dependency.getOwnerModule().getName(), dependency.getName());
    Set<String> intellijPaths = ContainerUtilRt.newHashSet();
    GradleLibrary gradleLibrary = dependency.getTarget();
    Library intellijLibrary = null;
    if (intellijDependency == null) {
      context.entities.add(dependency);
    }
    else {
      intellijLibrary = intellijDependency.getLibrary();
    }

    if (intellijLibrary == null) {
      intellijLibrary = myProjectStructureHelper.findIdeLibrary(gradleLibrary);
    }

    if (intellijLibrary == null) {
      context.entities.add(gradleLibrary);
    }
    else {
      for (VirtualFile jarFile : intellijLibrary.getFiles(OrderRootType.CLASSES)) {
        intellijPaths.add(GradleUtil.getLocalFileSystemPath(jarFile));
      }
    }
    
    for (String gradleJarPath : gradleLibrary.getPaths(LibraryPathType.BINARY)) {
      if (!intellijPaths.contains(gradleJarPath)) {
        context.entities.add(new GradleJar(gradleJarPath, LibraryPathType.BINARY, null, gradleLibrary));
      }
    }
  }

  public void removeNodes(@NotNull Collection<GradleProjectStructureNode<?>> nodes) {
    Collection<Object> entities = ContainerUtilRt.newArrayList();
    for (GradleProjectStructureNode<?> node : nodes) {
      GradleProjectStructureNodeDescriptor<? extends GradleEntityId> descriptor = node.getDescriptor();
      if (descriptor.getAttributes() != GradleTextAttributes.INTELLIJ_LOCAL_CHANGE) {
        continue;
      }
      Object entity = myIdMapper.mapIdToEntity(descriptor.getElement());
      if (entity != null) {
        entities.add(entity);
      }
    }
    myEntityManageHelper.removeEntities(entities, true);
  }
  
  private class Context {
    
    public final Set<GradleEntity> entities = ContainerUtilRt.newHashSet();
    public final CollectingVisitor visitor = new CollectingVisitor(this);
    public boolean recursive;
  }

  private class CollectingVisitor implements GradleEntityVisitor {
    @NotNull private final Context myContext;

    CollectingVisitor(@NotNull Context context) {
      myContext = context;
    }

    @Override public void visit(@NotNull GradleProject project) { }
    @Override public void visit(@NotNull GradleModule module) { collectModuleEntities(module, myContext); }
    @Override public void visit(@NotNull GradleContentRoot contentRoot) { collectContentRoots(contentRoot, myContext); }
    @Override public void visit(@NotNull GradleLibrary library) { /* Assuming that a library may be imported only as a dependency */ }
    @Override public void visit(@NotNull GradleModuleDependency dependency) { collectModuleDependencyEntities(dependency, myContext); }
    @Override public void visit(@NotNull GradleLibraryDependency dependency) { collectLibraryDependencyEntities(dependency, myContext); }
    @Override public void visit(@NotNull GradleJar jar) { myContext.entities.add(jar); }
    @Override public void visit(@NotNull GradleCompositeLibraryDependency dependency) { /* Do nothing */ }
  }
}
