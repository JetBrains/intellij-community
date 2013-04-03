package org.jetbrains.plugins.gradle.ui;

import com.intellij.openapi.externalSystem.ui.ProjectStructureNode;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 3/11/12 11:40 AM
 */
public interface GradleProjectStructureNodeFilter {

  boolean isVisible(@NotNull ProjectStructureNode<?> node);
}
