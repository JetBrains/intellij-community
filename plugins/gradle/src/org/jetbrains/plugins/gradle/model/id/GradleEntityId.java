package org.jetbrains.plugins.gradle.model.id;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;

/**
 * Represent unique identifier object for any gralde or intellij project structure entity (module, library, library dependency etc).
 * <p/>
 * We need an entity id, for example, at the <code>'sync project structure'</code> tree - the model needs to keep mappings
 * between the existing tree nodes and corresponding project structure entities. However, we can't keep the entity as is because
 * it may cause memory leak and be not safe (the entity's hash code may be changed).
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/14/12 12:26 PM
 */
public interface GradleEntityId {

  @NotNull
  GradleEntityType getType();

  @NotNull
  GradleEntityOwner getOwner();

  void setOwner(@NotNull GradleEntityOwner owner);

  @Nullable
  Object mapToEntity(@NotNull GradleEntityMappingContext context);
}
