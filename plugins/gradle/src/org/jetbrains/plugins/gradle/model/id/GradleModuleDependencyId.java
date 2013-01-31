package org.jetbrains.plugins.gradle.model.id;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;

/**
 * @author Denis Zhdanov
 * @since 2/14/12 2:06 PM
 */
public class GradleModuleDependencyId extends AbstractGradleDependencyId {

  public GradleModuleDependencyId(@NotNull GradleEntityOwner owner, @NotNull String ownerModuleName, @NotNull String dependencyModuleName) {
    super(GradleEntityType.MODULE_DEPENDENCY, owner, ownerModuleName, dependencyModuleName);
  }

  @Override
  public Object mapToEntity(@NotNull GradleProjectStructureContext context) {
    switch (getOwner()) {
      case GRADLE: return context.getProjectStructureHelper().findGradleModuleDependency(getOwnerModuleName(), getDependencyName());
      case IDE: return context.getProjectStructureHelper().findIdeModuleDependency(getOwnerModuleName(), getDependencyName());
    }
    throw new IllegalStateException(String.format(
      "Can't map id to the target module dependency. Owner: %s, owner module: '%s', dependency module: '%s'",
      getOwner(), getOwnerModuleName(), getDependencyName()
    ));
  }

  @Override
  public String toString() {
    return String.format("module dependency:owner module='%s'|dependency module='%s'", getOwnerModuleName(), getDependencyName());
  }
}
