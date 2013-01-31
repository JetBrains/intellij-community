package org.jetbrains.plugins.gradle.model.id;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;

/**
 * @author Denis Zhdanov
 * @since 2/14/12 1:55 PM
 */
public class GradleModuleId extends AbstractGradleEntityId {

  @NotNull private final String myModuleName;
  
  public GradleModuleId(@NotNull GradleEntityOwner owner, @NotNull String moduleName) {
    super(GradleEntityType.MODULE, owner);
    myModuleName = moduleName;
  }

  @Override
  public Object mapToEntity(@NotNull GradleProjectStructureContext context) {
    switch (getOwner()) {
      case GRADLE: return context.getProjectStructureHelper().findGradleModule(myModuleName);
      case IDE: return context.getProjectStructureHelper().findIdeModule(myModuleName);
    }
    throw new IllegalStateException(String.format(
      "Can't map module id to the target module. Id owner: %s, name: '%s'", getOwner(), myModuleName
    ));
  }

  @NotNull
  public String getModuleName() {
    return myModuleName;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myModuleName.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    GradleModuleId that = (GradleModuleId)o;
    return myModuleName.equals(that.myModuleName);
  }

  @Override
  public String toString() {
    return "module '" + myModuleName + "'";
  }
}
