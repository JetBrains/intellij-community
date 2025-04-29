// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import org.jetbrains.annotations.NotNull;
import com.intellij.gradle.toolingExtension.model.repositoryModel.ProjectRepositoriesModel;

import java.io.Serializable;
import java.util.List;

/**
 * Container for Maven repositories of a given project
 * @deprecated use {@link ProjectRepositoriesModel} instead.
 */
@Deprecated
public interface RepositoryModels extends Serializable {

  @NotNull
  List<MavenRepositoryModel> getRepositories();
}
