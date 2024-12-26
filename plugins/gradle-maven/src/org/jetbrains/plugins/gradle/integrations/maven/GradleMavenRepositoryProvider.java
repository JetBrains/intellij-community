// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.integrations.maven;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.indices.MavenRepositoryProvider;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;

import java.util.Set;

/**
 * @author Vladislav.Soroka
 */
public class GradleMavenRepositoryProvider implements MavenRepositoryProvider {

  @Override
  public @NotNull Set<MavenRemoteRepository> getRemoteRepositories(@NotNull Project project) {
    return MavenRepositoriesHolder.getInstance(project).getRemoteRepositories();
  }
}
