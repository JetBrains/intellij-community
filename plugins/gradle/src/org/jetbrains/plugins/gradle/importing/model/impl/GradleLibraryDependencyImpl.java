package org.jetbrains.plugins.gradle.importing.model.impl;

import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.importing.model.GradleEntityVisitor;
import org.jetbrains.plugins.gradle.importing.model.GradleLibraryDependency;
import org.jetbrains.plugins.gradle.importing.model.LibraryPathType;

import java.io.File;
import java.io.Serializable;
import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:46 PM
 */
public class GradleLibraryDependencyImpl extends AbstractGradleDependency implements GradleLibraryDependency, Serializable {

  private static final long serialVersionUID = 1L;

  private final Map<LibraryPathType, String> myPaths = new HashMap<LibraryPathType, String>();
  
  private String myName;

  public GradleLibraryDependencyImpl(@NotNull String name) {
    myName = name;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void setName(@NotNull String name) {
    myName = name;
  }

  @Nullable
  @Override
  public String getPath(@NotNull LibraryPathType type) {
    return myPaths.get(type);
  }
  
  public void addPath(@NotNull LibraryPathType type, @NotNull String path) {
    myPaths.put(type, new File(path).getAbsolutePath());
  }

  @Override
  public void invite(@NotNull GradleEntityVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public int hashCode() {
    int result = myPaths.hashCode();
    result = 31 * result + myName.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleLibraryDependencyImpl that = (GradleLibraryDependencyImpl)o;
    return myName.equals(that.myName) && myPaths.equals(that.myPaths);
  }

  @Override
  public String toString() {
    return super.toString() + ", library: " + getName();
  }
}
