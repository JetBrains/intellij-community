package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/10/11 6:32 PM
 */
public interface GradleModuleDependency extends GradleDependency {

  @NotNull
  GradleModule getModule();
}
