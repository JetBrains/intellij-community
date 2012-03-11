package org.jetbrains.plugins.gradle.ui;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 3/11/12 11:40 AM
 */
public interface GradleProjectStructureNodeFilter {

  boolean isVisible(@NotNull GradleProjectStructureNode<?> node);
}
