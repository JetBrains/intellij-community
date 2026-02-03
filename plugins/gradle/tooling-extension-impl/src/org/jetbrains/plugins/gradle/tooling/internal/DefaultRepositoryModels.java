// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.tooling.internal;

import com.intellij.gradle.toolingExtension.impl.model.repositoryModel.DefaultProjectRepositoriesModel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.MavenRepositoryModel;
import org.jetbrains.plugins.gradle.model.RepositoryModels;

import java.util.Collections;
import java.util.List;

/**
 * @deprecated use {@link DefaultProjectRepositoriesModel} instead.
 */
@Deprecated
public class DefaultRepositoryModels implements RepositoryModels {

  private final @NotNull List<MavenRepositoryModel> myRepositories;
  private final int myRepositoriesHashCode;

  public DefaultRepositoryModels(@NotNull List<MavenRepositoryModel> repositories) {
    myRepositories = Collections.unmodifiableList(repositories);
    myRepositoriesHashCode = myRepositories.hashCode();
  }

  @Override
  public @NotNull List<MavenRepositoryModel> getRepositories() {
    return myRepositories;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DefaultRepositoryModels model = (DefaultRepositoryModels)o;
    if (myRepositoriesHashCode != model.myRepositoriesHashCode) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return myRepositoriesHashCode;
  }
}
