package org.jetbrains.plugins.gradle.model;

import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Not thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 8/24/11 4:50 PM
 */
public class GradleLibrary extends AbstractNamedGradleEntity implements Named {

  private static final long serialVersionUID = 1L;

  private final Map<LibraryPathType, Set<String>> myPaths = new HashMap<LibraryPathType, Set<String>>();

  public GradleLibrary(@NotNull String name) {
    super(name);
  }

  @NotNull
  public Set<String> getPaths(@NotNull LibraryPathType type) {
    Set<String> result = myPaths.get(type);
    return result == null ? Collections.<String>emptySet() : result;
  }

  public void addPath(@NotNull LibraryPathType type, @NotNull String path) {
    Set<String> paths = myPaths.get(type);
    if (paths == null) {
      myPaths.put(type, paths = new HashSet<String>());
    } 
    paths.add(GradleUtil.toCanonicalPath(path));
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
      for (Map.Entry<LibraryPathType, Set<String>> entry : myPaths.entrySet()) {
        for (String path : entry.getValue()) {
          result.addPath(entry.getKey(), path);
        }
      }
    } 
    return result;
  }
}
