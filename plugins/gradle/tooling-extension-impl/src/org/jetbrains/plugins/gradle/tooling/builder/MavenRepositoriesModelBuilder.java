// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.builder;

import com.intellij.gradle.toolingExtension.impl.modelBuilder.Messages;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.MavenRepositoryModel;
import org.jetbrains.plugins.gradle.model.RepositoryModels;
import org.jetbrains.plugins.gradle.tooling.Message;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderContext;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;
import org.jetbrains.plugins.gradle.tooling.internal.DefaultRepositoryModels;
import org.jetbrains.plugins.gradle.tooling.internal.MavenRepositoryModelImpl;

import java.util.ArrayList;
import java.util.List;

/**
 * @deprecated this model builder will be replaced by {@link ProjectRepositoriesModelBuilder}.
 */
@Deprecated
public class MavenRepositoriesModelBuilder implements ModelBuilderService {
  @Override
  public boolean canBuild(String modelName) {
    return RepositoryModels.class.getName().equals(modelName);
  }

  @Override
  public Object buildAll(String modelName, Project project) {
    List<MavenRepositoryModel> repositories = new ArrayList<>();
    for (MavenArtifactRepository artifactRepository : project.getRepositories().withType(MavenArtifactRepository.class)) {
      final MavenRepositoryModel model = new MavenRepositoryModelImpl(artifactRepository.getName(), artifactRepository.getUrl().toString());
      repositories.add(model);
    }
    return new DefaultRepositoryModels(repositories);
  }

  @Override
  public void reportErrorMessage(
    @NotNull String modelName,
    @NotNull Project project,
    @NotNull ModelBuilderContext context,
    @NotNull Exception exception
  ) {
    context.getMessageReporter().createMessage()
      .withGroup(Messages.MAVEN_REPOSITORY_MODEL_GROUP)
      .withKind(Message.Kind.WARNING)
      .withTitle("Project maven repositories import failure")
      .withText("Unable to obtain information about configured Maven repositories")
      .withException(exception)
      .reportMessage(project);
  }
}
