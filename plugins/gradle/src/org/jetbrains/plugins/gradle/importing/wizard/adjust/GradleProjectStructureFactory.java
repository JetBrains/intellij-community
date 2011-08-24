package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Ref;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.*;

import javax.swing.*;
import javax.swing.tree.DefaultTreeModel;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collection;

/**
 * Allows to build various entities related to the 'project structure' view elements.
 * <p/>
 * Thread-safe.
 * <p/>
 * This class is not singleton but offers single-point-of-usage field - {@link #INSTANCE}.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 2:52 PM
 */
public class GradleProjectStructureFactory {

  /** Shared instance of the current (stateless) class. */
  public static final GradleProjectStructureFactory INSTANCE = new GradleProjectStructureFactory();

  private static final Icon PROJECT_ICON = IconLoader.getIcon("/nodes/ideaProject.png");
  private static final Icon MODULE_ICON  = IconLoader.getIcon("/nodes/ModuleOpen.png");
  private static final Icon LIB_ICON     = IconLoader.getIcon("/nodes/ppLib.png");

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public <T extends GradleEntity> GradleProjectStructureNodeDescriptor buildDescriptor(@NotNull T entity) {
    final Ref<GradleProjectStructureNodeDescriptor> result = new Ref<GradleProjectStructureNodeDescriptor>();
    entity.invite(new GradleEntityVisitor() {
      @Override
      public void visit(@NotNull GradleProject project) {
        result.set(new GradleProjectStructureNodeDescriptor(project, project.getName(), PROJECT_ICON));
      }

      @Override
      public void visit(@NotNull GradleModule module) {
        result.set(new GradleProjectStructureNodeDescriptor(module, module.getName(), MODULE_ICON));
      }

      @Override
      public void visit(@NotNull GradleContentRoot contentRoot) {
        // TODO den implement 
      }

      @Override
      public void visit(@NotNull GradleModuleDependency dependency) {
        result.set(new GradleProjectStructureNodeDescriptor(dependency, dependency.getModule().getName(), MODULE_ICON));
      }

      @Override
      public void visit(@NotNull GradleLibraryDependency dependency) {
        result.set(new GradleProjectStructureNodeDescriptor(dependency, dependency.getName(), LIB_ICON));
      }
    });
    return result.get();
  }

  @SuppressWarnings("MethodMayBeStatic")
  @NotNull
  public GradleProjectStructureNodeSettings buildSettings(@NotNull GradleEntity entity,
                                                          @NotNull final DefaultTreeModel treeModel,
                                                          @NotNull final Collection<GradleProjectStructureNode> treeNodes)
  {
    // TODO den remove
    final GradleProjectStructureNodeSettings toRemove = new GradleProjectStructureNodeSettings() {
      @Override
      public boolean validate() {
        return true;
      }

      @NotNull
      @Override
      public JComponent getComponent() {
        return new JLabel("xxxxxxxxx" + this);
      }
    };
    final Ref<GradleProjectStructureNodeSettings> result = new Ref<GradleProjectStructureNodeSettings>();
    entity.invite(new GradleEntityVisitor() {
      @Override
      public void visit(@NotNull GradleProject project) {
        result.set(new GradleProjectSettings(project));
      }

      @Override
      public void visit(@NotNull GradleModule module) {
        result.set(new GradleModuleSettings(wrap(GradleModule.class, module, treeModel, treeNodes))); 
      }

      @Override
      public void visit(@NotNull GradleContentRoot contentRoot) {
        // TODO den implement
        result.set(toRemove);
      }

      @Override
      public void visit(@NotNull GradleModuleDependency dependency) {
        result.set(new GradleModuleSettings(wrap(GradleModule.class, dependency.getModule(), treeModel, treeNodes)));
      }

      @Override
      public void visit(@NotNull GradleLibraryDependency dependency) {
        result.set(new GradleLibrarySettings(wrap(GradleLibraryDependency.class, dependency, treeModel, treeNodes)));
      }
    });
    return result.get();
  }

  /**
   * Wraps target entity into proxy that handles logic of UI update for the corresponding nodes.
   * 
   * @param interfaceClass  target entity business interface
   * @param delegate        target entity to wrap
   * @param model           model of the target tree
   * @param treeNodes       tree nodes that represent the given entity
   * @param <T>             target entity business interface
   * @return                UI-aware proxy of the given entity
   */
  @SuppressWarnings("unchecked")
  private static <T> T wrap(@NotNull Class<T> interfaceClass, @NotNull final T delegate, @NotNull final DefaultTreeModel model,
                            @NotNull final Collection<GradleProjectStructureNode> treeNodes)
  {
    final Method triggerMethod;
    try {
      triggerMethod = Named.class.getMethod("setName", String.class);
    }
    catch (NoSuchMethodException e) {
      // Never expect to be here.
      throw new RuntimeException("Unexpected exception occurred", e);
    }
    InvocationHandler invocationHandler = new InvocationHandler() {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result = method.invoke(delegate, args);
        if (method.equals(triggerMethod)) {
          for (GradleProjectStructureNode node : treeNodes) {
            node.getDescriptor().setName(args[0].toString());
            model.nodeChanged(node);
          }
        }
        return result;
      }
    };
    ClassLoader classLoader = GradleProjectStructureFactory.class.getClassLoader();
    Class<?>[] interfaces = {interfaceClass};
    return (T)Proxy.newProxyInstance(classLoader, interfaces, invocationHandler);
  }
}
