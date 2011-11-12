package org.jetbrains.plugins.gradle.sync;

/**
 * Defines common interface for the change between Gradle and IntelliJ IDEA project structures.
 * <p/>
 * Implementations of this interface are not obliged to be thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 11/3/11 3:54 PM
 */
public interface GradleProjectStructureChange {

  // TODO den add doc
  boolean isConfirmed();
}
