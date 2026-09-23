// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;

/**
 * Service provider interface for resolving additional source and Javadoc artifacts
 * that the standard {@link AuxiliaryArtifactResolver} cannot find.
 * <p>
 * This is useful for dependencies whose source artifacts are published under
 * different Maven coordinates than the main artifact (e.g., IntelliJ Platform dependencies).
 * <p>
 * Implementations are discovered via {@link java.util.ServiceLoader}.
 */
@ApiStatus.Internal
public interface AuxiliaryArtifactProvider {

  /**
   * Resolves additional artifacts for {@code configuration}.
   *
   * @deprecated Override {@link #resolve(ModelBuilderContext, Project, Configuration, GradleDependencyDownloadPolicy)}.
   * This method stays for providers that were compiled against an older IDE.
   */
  @Deprecated
  @SuppressWarnings("unused")
  default @NotNull AuxiliaryConfigurationArtifacts resolve(
    @NotNull Project project,
    @NotNull Configuration configuration,
    @NotNull GradleDependencyDownloadPolicy policy
  ) {
    return AuxiliaryConfigurationArtifacts.EMPTY;
  }

  /**
   * Resolves additional artifacts for {@code configuration}.
   * Use {@code context} to share data within the build or to report messages to the IDE.
   * By default, calls {@link #resolve(Project, Configuration, GradleDependencyDownloadPolicy)}.
   */
  default @NotNull AuxiliaryConfigurationArtifacts resolve(
    @NotNull ModelBuilderContext context,
    @NotNull Project project,
    @NotNull Configuration configuration,
    @NotNull GradleDependencyDownloadPolicy policy
  ) {
    return resolve(project, configuration, policy);
  }
}
