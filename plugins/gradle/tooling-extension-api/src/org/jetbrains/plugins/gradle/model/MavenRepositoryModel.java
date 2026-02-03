// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import com.intellij.gradle.toolingExtension.model.repositoryModel.RepositoryModel;

import java.io.Serializable;

/**
 * Maven repositories configured for the project
 * @deprecated Use {@link RepositoryModel} instead.
 */
@Deprecated
public interface MavenRepositoryModel extends Serializable {
  String getName();

  String getUrl();
}
