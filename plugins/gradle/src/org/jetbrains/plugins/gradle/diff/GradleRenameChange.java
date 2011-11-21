package org.jetbrains.plugins.gradle.diff;

import org.jetbrains.annotations.NotNull;

/**
 * Describes particular entity name change.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:54 PM
 */
public class GradleRenameChange extends GradleAbstractPropertyValueChange<String> {

  public enum Entity { PROJECT, MODULE, LIBRARY }

  private final Entity myEntity;
  
  public GradleRenameChange(@NotNull Entity entity, @NotNull String gradleName, @NotNull String intellijName) {
    super(entity.toString().toLowerCase(), gradleName, intellijName);
    myEntity = entity;
  }

  @NotNull
  public Entity getEntity() {
    return myEntity;
  }

  @Override
  public int hashCode() {
    return 31 * super.hashCode() + myEntity.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleRenameChange that = (GradleRenameChange)o;
    return myEntity.equals(that.myEntity);
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }
}
