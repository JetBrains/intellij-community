package org.jetbrains.plugins.gradle.importing.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleEntityVisitor;
import org.jetbrains.plugins.gradle.importing.model.GradleLibrary;
import org.jetbrains.plugins.gradle.importing.model.GradleLibraryDependency;

import java.io.Serializable;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:46 PM
 */
public class GradleLibraryDependencyImpl extends AbstractGradleDependency implements GradleLibraryDependency, Serializable {

  private final GradleLibrary myLibrary;

  public GradleLibraryDependencyImpl(@NotNull GradleLibrary library) {
    myLibrary = library;
  }

  @NotNull
  @Override
  public GradleLibrary getLibrary() {
    return myLibrary;
  }

  @Override
  public void invite(@NotNull GradleEntityVisitor visitor) {
    visitor.visit(this); 
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myLibrary.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    GradleLibraryDependencyImpl that = (GradleLibraryDependencyImpl)o;
    return myLibrary.equals(that.myLibrary);
  }

  @Override
  public String toString() {
    return super.toString() + ", dependency library: " + getLibrary();
  }
}
