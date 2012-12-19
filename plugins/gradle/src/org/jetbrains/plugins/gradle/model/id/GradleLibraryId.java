package org.jetbrains.plugins.gradle.model.id;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;

/**
 * @author Denis Zhdanov
 * @since 2/20/12 7:12 PM
 */
public class GradleLibraryId extends GradleAbstractEntityId {

  @NotNull private final String myLibraryName;
  
  public GradleLibraryId(@NotNull GradleEntityOwner owner, @NotNull String libraryName) {
    super(GradleEntityType.LIBRARY, owner);
    myLibraryName = libraryName;
  }

  @NotNull
  public String getLibraryName() {
    return myLibraryName;
  }

  @Override
  public Object mapToEntity(@NotNull GradleProjectStructureContext context) {
    switch (getOwner()) {
      case GRADLE: return context.getProjectStructureHelper().findGradleLibrary(myLibraryName);
      case INTELLIJ: return context.getProjectStructureHelper().findIntellijLibrary(myLibraryName);
    }
    throw new IllegalStateException(String.format(
      "Can't map library id to the target library. Id owner: %s, name: '%s'", getOwner(), myLibraryName
    ));
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
    GradleLibraryId that = (GradleLibraryId)o;
    return myLibraryName.equals(that.myLibraryName);
  }

  @Override
  public String toString() {
    return "library '" + myLibraryName + "'";
  }
}
