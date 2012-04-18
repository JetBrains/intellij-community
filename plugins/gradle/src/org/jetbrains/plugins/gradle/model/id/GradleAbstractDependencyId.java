package org.jetbrains.plugins.gradle.model.id;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;

/**
 * @author Denis Zhdanov
 * @since 2/20/12 12:01 PM
 */
public abstract  class GradleAbstractDependencyId extends GradleAbstractEntityId {

  @NotNull private final String myOwnerModuleName;
  @NotNull private final String myDependencyName;
  
  public GradleAbstractDependencyId(@NotNull GradleEntityType type,
                                    @NotNull GradleEntityOwner owner,
                                    @NotNull String ownerModuleName,
                                    @NotNull String dependencyName)
  {
    super(type, owner);
    myOwnerModuleName = ownerModuleName;
    myDependencyName = dependencyName;
  }

  @NotNull
  public String getOwnerModuleName() {
    return myOwnerModuleName;
  }

  @NotNull
  public String getDependencyName() {
    return myDependencyName;
  }

  @NotNull
  public GradleModuleId getOwnerModuleId() {
    return new GradleModuleId(getOwner(), myOwnerModuleName);
  }
  
  @Override
  public int hashCode() {
    int result = 31 * super.hashCode() + myOwnerModuleName.hashCode();
    return 31 * result + myDependencyName.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    GradleAbstractDependencyId that = (GradleAbstractDependencyId)o;
    return myOwnerModuleName.equals(that.myOwnerModuleName) && myDependencyName.equals(that.myDependencyName);
  }
}
