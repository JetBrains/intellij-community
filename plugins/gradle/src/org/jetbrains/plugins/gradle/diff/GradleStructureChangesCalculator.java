package org.jetbrains.plugins.gradle.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleEntity;

import java.util.Set;

/**
 * Defines common interface to the strategy that calculates difference between the corresponding gradle and intellij entities
 * (e.g. between the gradle and intellij module).
 * <p/>
 * Implementations of this interface are expected to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/24/12 12:15 PM
 * @param <I>   target intellij entity type
 * @param <G>   target gradle entity type
 */
public interface GradleStructureChangesCalculator<G extends GradleEntity, I> {

  // TODO den add doc
  @NotNull
  Set<GradleProjectStructureChange> calculate(@NotNull G gradleEntity,
                                              @NotNull I intellijEntity,
                                              @NotNull Set<GradleProjectStructureChange> knownChanges);

  // TODO den add doc
  @NotNull
  Object getIntellijKey(@NotNull I entity, @NotNull Set<GradleProjectStructureChange> knownChanges);
  
  // TODO den add doc
  @NotNull
  Object getGradleKey(@NotNull G entity, @NotNull Set<GradleProjectStructureChange> knownChanges);
}
