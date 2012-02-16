package org.jetbrains.plugins.gradle.model.id;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;

/**
 * @author Denis Zhdanov
 * @since 2/14/12 1:34 PM
 */
public class GradleProjectId extends GradleAbstractEntityId {

  public GradleProjectId(@NotNull GradleEntityOwner owner) {
    super(GradleEntityType.PROJECT, owner);
  }

  @Override
  public Object mapToEntity(@NotNull GradleEntityMappingContext context) {
    switch (getOwner()) {
      case GRADLE: return context.getChangesModel().getGradleProject();
      case INTELLIJ: return context.getProjectStructureHelper().getProject();
    }
    throw new IllegalStateException(String.format("Can't map project id to the target project. Id owner: %s", getOwner()));
  }
}
