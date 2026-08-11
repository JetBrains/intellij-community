// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.toolingExtension.impl.model.dependencyModel.auxiliary;

import com.intellij.gradle.toolingExtension.impl.model.dependencyDownloadPolicyModel.GradleDependencyDownloadPolicy;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

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

  @NotNull
  AuxiliaryConfigurationArtifacts resolve(
    @NotNull Project project,
    @NotNull Configuration configuration,
    @NotNull GradleDependencyDownloadPolicy policy
  );
}
