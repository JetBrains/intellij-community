package org.jetbrains.plugins.gradle.model;

import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * This class serves as an id for any library dependency. We can't use library object itself because its hashCode()/equals() may
 * be changed over time (e.g. source root is added).
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/6/12 12:09 PM
 */
public class GradleLibraryDependencyId {
  
  private final String myLibraryName;
  private final String myModuleName;

  public GradleLibraryDependencyId(@NotNull String libraryName, @NotNull String moduleName) {
    myLibraryName = libraryName;
    myModuleName = moduleName;
  }

  @Nullable
  public static GradleLibraryDependencyId of(@Nullable GradleLibraryDependency dependency) {
    return dependency == null ? null : new GradleLibraryDependencyId(dependency.getName(), dependency.getOwnerModule().getName());
  }

  @Nullable
  public static GradleLibraryDependencyId of(@Nullable LibraryOrderEntry dependency) {
    if (dependency == null) {
      return null;
    }
    final String libraryName = dependency.getLibraryName();
    if (libraryName == null || StringUtil.isEmpty(libraryName)) {
      return null;
    }
    return new GradleLibraryDependencyId(libraryName, dependency.getOwnerModule().getName());
  }  
  
  @NotNull
  public String getLibraryName() {
    return myLibraryName;
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }
  
  @Override
  public int hashCode() {
    int result = myLibraryName.hashCode();
    result = 31 * result + myModuleName.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleLibraryDependencyId id = (GradleLibraryDependencyId)o;

    if (!myLibraryName.equals(id.myLibraryName)) return false;
    if (!myModuleName.equals(id.myModuleName)) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format("library dependency: library='%s', module='%s'", myLibraryName, myModuleName);
  }
}
