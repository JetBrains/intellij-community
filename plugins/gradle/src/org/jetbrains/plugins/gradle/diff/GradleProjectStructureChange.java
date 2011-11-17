package org.jetbrains.plugins.gradle.diff;

import org.jetbrains.annotations.NotNull;

/**
 * Defines common interface for the change between Gradle and IntelliJ IDEA project structures.
 * <p/>
 * Implementations of this interface are assumed to provide correct {@link #hashCode()}/{@link #equals(Object)}.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:54 PM
 */
public interface GradleProjectStructureChange {

  /**
   * @return    <code>true</code> if the change is {@link #confirm() confirmed}; <code>false</code> otherwise
   */
  boolean isConfirmed();

  /**
   * Allows to confirm current change, i.e. indicate that end-user checked the change and consider it to remain.
   */
  void confirm();

  /**
   * Allows to perform double-dispatch of the current change object within the given visitor.
   *
   * @param visitor  visitor to use
   */
  void invite(@NotNull GradleProjectStructureChangeVisitor visitor);
}
