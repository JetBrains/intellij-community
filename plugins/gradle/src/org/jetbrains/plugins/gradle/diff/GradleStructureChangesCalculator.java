package org.jetbrains.plugins.gradle.diff;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleEntity;

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

  /**
   * Calculates changes between the given entities.
   * 
   * @param gradleEntity    target gradle entity
   * @param intellijEntity  target intellij entity
   * @param knownChanges    changes between the gradle and intellij project structure that has been known up until now
   * @param currentChanges  holder for the changes between the given entities discovered by the current call. Note that
   *                        it must contain the change objects that have been known (contained at the <code>'knownChanges'</code>)
   *                        but are still in place
   */
  void calculate(@NotNull G gradleEntity,
                 @NotNull I intellijEntity,
                 @NotNull Set<GradleProjectStructureChange> knownChanges,
                 @NotNull Set<GradleProjectStructureChange> currentChanges);

  /**
   * There are three possible situations when we compare a set of gradle entities with a set of intellij entities:
   * <pre>
   * <ul>
   *   <li>particular entity presents only at the gradle side;</li>
   *   <li>particular entity presents only at the intellij side;</li>
   *   <li>particular gradle entity is matched to particular intellij entity (they may have difference in their settings though);</li>
   * </ul>
   * </pre>
   * <p/>
   * The general idea is to map evey item at the given sets of gradle and intellij entities to particular key (both gradle and
   * intellij keys are expected to belong to the same class) and then compare them. Matched keys shows that corresponding
   * entities should be {@link #calculate(GradleEntity, Object, Set, Set)}  compared to each other}; non-matched indicate that corresponding
   * entities are gradle- or intellij-local.
   * <p/>
   * This method allows to match intellij entity to the target key.
   * 
   * @param entity  intellij entity to match
   * @return        key for the given entity
   * @see #getGradleKey(GradleEntity, Set)
   */
  @NotNull
  Object getIntellijKey(@NotNull I entity);

  /**
   * Serves the same purpose as {@link #getIntellijKey(Object)} but targets gradle entities.
   * <p/>
   * There is a possible case that two corresponding gradle and intellij entities differ from each other by the setting that
   * affects the resulting key (e.g. we may use module name as a key for 'module' entities and intellij module name differs from
   * the name of the corresponding gradle module). We need to match only in one direction then (e.g. consider a situation when
   * particular module is named differently at gradle and intellij. We shouldn't consider that change during both
   * {@code intellij-entity -> key} and {@code gradle-entity -> key} mappings because that would produce two different keys). 
   * So, we take into consideration the known changes only during {@code gradle-entity -> key} processing.
   * 
   * @param entity        target gradle entity that should be mapped to a key
   * @param knownChanges  known changes between the gradle and intellij structures
   * @return              key for the given entity
   * @see #getIntellijKey(Object)
   */
  @NotNull
  Object getGradleKey(@NotNull G entity, @NotNull Set<GradleProjectStructureChange> knownChanges);
}
