package org.jetbrains.plugins.gradle.importing.model;

import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.Map;

/**
 * @author Denis Zhdanov
 * @since 8/24/11 4:50 PM
 */
public class GradleLibrary extends AbstractNamedGradleEntity implements Named {

  private static final long serialVersionUID = 1L;

  private final Map<LibraryPathType, String> myPaths = new HashMap<LibraryPathType, String>();

  public GradleLibrary(@NotNull String name) {
    super(name);
  }

  @Nullable
  public String getPath(@NotNull LibraryPathType type) {
    return myPaths.get(type);
  }

  public void addPath(@NotNull LibraryPathType type, @NotNull String path) {
    myPaths.put(type, GradleUtil.toCanonicalPath(path));
  }

  @Override
  public void invite(@NotNull GradleEntityVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public int hashCode() {
    int result = myPaths.hashCode();
    result = 31 * result + super.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleLibrary that = (GradleLibrary)o;
    return super.equals(that) && myPaths.equals(that.myPaths);
  }

  @Override
  public String toString() {
    return "library: " + getName();
  }

  @NotNull
  @Override
  public GradleLibrary clone(@NotNull GradleEntityCloneContext context) {
    GradleLibrary result = context.getLibrary(this);
    if (result == null) {
      result = new GradleLibrary(getName());
      context.store(this, result);
      for (Map.Entry<LibraryPathType, String> entry : myPaths.entrySet()) {
        result.addPath(entry.getKey(), entry.getValue());
      }
    } 
    return result;
  }
}
