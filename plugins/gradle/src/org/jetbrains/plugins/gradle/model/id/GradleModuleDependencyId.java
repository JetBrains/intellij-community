package org.jetbrains.plugins.gradle.model.id;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;

/**
 * @author Denis Zhdanov
 * @since 2/14/12 2:06 PM
 */
public class GradleModuleDependencyId extends GradleAbstractEntityId {

  @NotNull private final String myOwnerModuleName;
  @NotNull private final String myDependencyModuleName;
  
  public GradleModuleDependencyId(@NotNull GradleEntityOwner owner, @NotNull String ownerModuleName, @NotNull String dependencyModuleName) {
    super(GradleEntityType.MODULE, owner);
    myOwnerModuleName = ownerModuleName;
    myDependencyModuleName = dependencyModuleName;
  }

  @Override
  public Object mapToEntity(@NotNull GradleEntityMappingContext context) {
    switch (getOwner()) {
      case GRADLE: return context.getProjectStructureHelper().findGradleModuleDependency(myOwnerModuleName, myDependencyModuleName);
      case INTELLIJ: return context.getProjectStructureHelper().findIntellijModuleDependency(myOwnerModuleName, myDependencyModuleName);
    }
    throw new IllegalStateException(String.format(
      "Can't map id to the target module dependency. Owner: %s, owner module: '%s', dependency module: '%s'",
      getOwner(), myOwnerModuleName, myDependencyModuleName
    ));
  }

  @Override
  public int hashCode() {
    int result = 31 * super.hashCode() + myOwnerModuleName.hashCode();
    return 31 * result + myDependencyModuleName.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    GradleModuleDependencyId that = (GradleModuleDependencyId)o;
    return myOwnerModuleName.equals(that.myOwnerModuleName) && myDependencyModuleName.equals(that.myDependencyModuleName);
  }

  @Override
  public String toString() {
    return String.format("module dependency:owner module='%s'|dependency module='%s'", myOwnerModuleName, myDependencyModuleName);
  }
}
