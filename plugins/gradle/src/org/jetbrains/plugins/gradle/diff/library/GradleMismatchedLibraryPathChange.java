package org.jetbrains.plugins.gradle.diff.library;

import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.diff.GradleAbstractConflictingPropertyChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import java.util.Set;

/**
 * @author Denis Zhdanov
 * @since 2/2/12 1:32 PM
 */
public class GradleMismatchedLibraryPathChange extends GradleAbstractConflictingPropertyChange<Set<String>> {
  
  private final String myLibraryName;

  public GradleMismatchedLibraryPathChange(@NotNull Library entity,
                                           @Nullable Set<String> gradleValue,
                                           @Nullable Set<String> intellijValue)
    throws IllegalArgumentException
  {
    super(GradleBundle.message("gradle.sync.change.library.path", entity.getName()), gradleValue, intellijValue);
    myLibraryName = entity.getName();
    if (myLibraryName == null) {
      throw new IllegalArgumentException(String.format("Can't create %s instance. Reason: given library has no name (%s)",
                                                       getClass().getName(), entity));
    }
  }

  @NotNull
  public String getLibraryName() {
    return myLibraryName;
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this); 
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myLibraryName.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    GradleMismatchedLibraryPathChange that = (GradleMismatchedLibraryPathChange)o;
    return myLibraryName.equals(that.myLibraryName);
  }
}
