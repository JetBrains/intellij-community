package org.jetbrains.plugins.gradle.model;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:40 PM
 */
public class GradleModuleDependency extends AbstractGradleDependency<GradleModule> {

  public static final Comparator<GradleModuleDependency> COMPARATOR = new Comparator<GradleModuleDependency>() {
    @Override
    public int compare(GradleModuleDependency o1, GradleModuleDependency o2) {
      return Named.COMPARATOR.compare(o1.getTarget(), o2.getTarget());
    }
  };
  
  private static final long serialVersionUID = 1L;
  
  public GradleModuleDependency(@NotNull GradleModule module) {
    super(module);
  }

  @Override
  public void invite(@NotNull GradleEntityVisitor visitor) {
    visitor.visit(this);
  }

  @NotNull
  @Override
  public GradleModuleDependency clone(@NotNull GradleEntityCloneContext context) {
    GradleModuleDependency result = new GradleModuleDependency(getTarget().clone(context));
    copyTo(result); 
    return result;
  }
}
