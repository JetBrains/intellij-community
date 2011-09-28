package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:46 PM
 */
public class GradleLibraryDependency extends AbstractGradleDependency {

  private final GradleLibrary myLibrary;

  public GradleLibraryDependency(@NotNull GradleLibrary library) {
    myLibrary = library;
  }

  @NotNull
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

    GradleLibraryDependency that = (GradleLibraryDependency)o;
    return myLibrary.equals(that.myLibrary);
  }

  @Override
  public String toString() {
    return super.toString() + ", dependency library: " + getLibrary();
  }

  @NotNull
  @Override
  public GradleLibraryDependency clone(@NotNull GradleEntityCloneContext context) {
    GradleLibraryDependency result = new GradleLibraryDependency(getLibrary().clone(context));
    copyTo(result);
    return result;
  }
}
