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
    int result = myScope.hashCode();
    result = 31 * result + (myExported ? 1 : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractGradleDependency that = (AbstractGradleDependency)o;

    if (myExported != that.myExported) return false;
    if (myScope != that.myScope) return false;

    return true;
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
