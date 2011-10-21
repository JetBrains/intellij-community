package org.jetbrains.plugins.gradle.importing.model;

import com.intellij.openapi.roots.DependencyScope;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:41 PM
 */
public abstract class AbstractGradleDependency extends AbstractGradleEntity implements GradleDependency {

  private static final long serialVersionUID = 1L;
  
  private DependencyScope myScope = DependencyScope.COMPILE;
  private boolean myExported;
  
  @Override
  @NotNull
  public DependencyScope getScope() {
    return myScope;
  }

  public void setScope(DependencyScope scope) {
    myScope = scope;
  }

  @Override
  public boolean isExported() {
    return myExported;
  }

  public void setExported(boolean exported) {
    myExported = exported;
  }

  @Override
  public int hashCode() {
    return 31;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o != null && getClass() == o.getClass());
  }

  @Override
  public String toString() {
    return "scope: " + getScope() + ", exported: " + isExported();
  }
  
  @NotNull
  @Override
  public GradleDependency clone() {
    try {
      return (GradleDependency)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  protected void copyTo(@NotNull AbstractGradleDependency that) {
    that.setExported(isExported());
    that.setScope(getScope());
  }
}
