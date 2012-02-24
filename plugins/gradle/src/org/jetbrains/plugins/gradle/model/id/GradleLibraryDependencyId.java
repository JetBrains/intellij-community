package org.jetbrains.plugins.gradle.model.id;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;

/**
 * @author Denis Zhdanov
 * @since 2/14/12 1:59 PM
 */
public class GradleLibraryDependencyId extends GradleAbstractDependencyId {

  public GradleLibraryDependencyId(@NotNull GradleEntityOwner owner, @NotNull String moduleName, @NotNull String libraryName) {
    super(GradleEntityType.LIBRARY_DEPENDENCY, owner, moduleName, libraryName);
  }

  @Override
  public Object mapToEntity(@NotNull GradleProjectStructureContext context) {
    switch (getOwner()) {
      case GRADLE: return context.getProjectStructureHelper().findGradleLibraryDependency(getOwnerModuleName(), getDependencyName());
      case INTELLIJ: return context.getProjectStructureHelper().findIntellijLibraryDependency(getOwnerModuleName(), getDependencyName());
    }
    throw new IllegalStateException(String.format(
      "Can't map id to the target library dependency. Owner: %s, module: '%s', library: '%s'",
      getOwner(), getOwnerModuleName(), getDependencyName()
    ));
  }

  @Override
  public String toString() {
    return String.format("library dependency:owner module='%s'|library='%s'", getOwnerModuleName(), getDependencyName());
  }
}
