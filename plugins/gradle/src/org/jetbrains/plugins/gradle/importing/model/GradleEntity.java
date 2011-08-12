package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;

/**
 * Stands for the entity from the 'import from gradle' domain.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 12:50 PM
 */
public interface GradleEntity {

  void invite(@NotNull GradleEntityVisitor visitor);
}
