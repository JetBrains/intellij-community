// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.List;

/**
 * @author Vladislav.Soroka
 */
public interface GradleConfiguration extends Serializable {
  @NotNull
  String getName();

  @Nullable
  String getDescription();

  boolean isVisible();

  boolean isScriptClasspathConfiguration();

  @NotNull List<String> getDeclarationAlternatives();

  /**
   * @return `true` for Gradle 8.2+ if a configuration can have dependencies declared. E.g., if it's a scope or an annotation processor.
   * For Gradle < 8.2, returns null because it's unclear whether a configuration could declare dependencies or not.
   * @see <a href="https://docs.gradle.org/current/kotlin-dsl/gradle/org.gradle.api.artifacts/-configuration/is-can-be-declared.html">Gradle Documentation</a>
   */
  @Nullable
  Boolean getCanBeDeclared();
}
