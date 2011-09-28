package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;

import java.util.Comparator;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:40 PM
 */
public class GradleModuleDependency extends AbstractGradleDependency {

  public static final Comparator<GradleModuleDependency> COMPARATOR = new Comparator<GradleModuleDependency>() {
    @Override
    public int compare(GradleModuleDependency o1, GradleModuleDependency o2) {
      return Named.COMPARATOR.compare(o1.getModule(), o2.getModule());
    }
  };
  
  private static final long serialVersionUID = 1L;
  
  private final GradleModule myModule;

  public GradleModuleDependency(@NotNull GradleModule module) {
    myModule = module;
  }

  @NotNull
  public GradleModule getModule() {
    return myModule;
  }

  @Override
  public void invite(@NotNull GradleEntityVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public int hashCode() {
    return myModule.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleModuleDependency that = (GradleModuleDependency)o;
    return myModule.equals(that.myModule);
  }

  @Override
  public String toString() {
    return super.toString() + ", dependency module: " + getModule();
  }

  @NotNull
  @Override
  public GradleModuleDependency clone(@NotNull GradleEntityCloneContext context) {
    GradleModuleDependency result = new GradleModuleDependency(getModule().clone(context));
    copyTo(result); 
    return result;
  }
}
