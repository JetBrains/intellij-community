package org.jetbrains.plugins.gradle.model.id;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;

/**
 * @author Denis Zhdanov
 * @since 2/14/12 1:59 PM
 */
public class GradleLibraryDependencyId extends GradleAbstractEntityId {

  @NotNull private final String myModuleName;
  @NotNull private final String myLibraryName;
  
  public GradleLibraryDependencyId(@NotNull GradleEntityOwner owner, @NotNull String moduleName, @NotNull String libraryName) {
    super(GradleEntityType.LIBRARY_DEPENDENCY, owner);
    myModuleName = moduleName;
    myLibraryName = libraryName;
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @NotNull
  public String getLibraryName() {
    return myLibraryName;
  }

  @NotNull
  public GradleModuleId getModuleId() {
    return new GradleModuleId(getOwner(), myModuleName);
  }
  
  @Override
  public Object mapToEntity(@NotNull GradleEntityMappingContext context) {
    switch (getOwner()) {
      case GRADLE: return context.getProjectStructureHelper().findGradleLibraryDependency(myModuleName, myLibraryName);
      case INTELLIJ: return context.getProjectStructureHelper().findIntellijLibraryDependency(myModuleName, myLibraryName);
    }
    throw new IllegalStateException(String.format(
      "Can't map id to the target library dependency. Owner: %s, module: '%s', library: '%s'", getOwner(), myModuleName, myLibraryName
    ));
  }

  @Override
  public int hashCode() {
    int result = 31 * super.hashCode() + myModuleName.hashCode();
    return 31 * result + myLibraryName.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    GradleLibraryDependencyId that = (GradleLibraryDependencyId)o;
    return myModuleName.equals(that.myModuleName) && myLibraryName.equals(that.myLibraryName);
  }

  @Override
  public String toString() {
    return String.format("library dependency:owner module='%s'|library='%s'", myModuleName, myLibraryName);
  }
}
