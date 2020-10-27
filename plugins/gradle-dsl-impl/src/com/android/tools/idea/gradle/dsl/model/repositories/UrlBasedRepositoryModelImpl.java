/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.model.repositories;

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.repositories.GoogleDefaultRepositoryModel;
import com.android.tools.idea.gradle.dsl.api.repositories.UrlBasedRepositoryModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.KtsOnlyPropertyTransform;
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SingleArgumentMethodTransform;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all the url based repository models like Maven and JCenter.
 */
public abstract class UrlBasedRepositoryModelImpl extends RepositoryModelImpl implements UrlBasedRepositoryModel {
  @NonNls public static final String URL = "url";
  @NonNls public static final String ARTIFACT_URLS = "artifactUrls";

  @NotNull private final String myDefaultRepoUrl;

  protected UrlBasedRepositoryModelImpl(@NotNull GradlePropertiesDslElement holder,
                                        @NotNull GradlePropertiesDslElement dslElement,
                                        @NotNull String defaultRepoName,
                                        @NotNull String defaultRepoUrl) {
    super(holder, dslElement, defaultRepoName);
    myDefaultRepoUrl = defaultRepoUrl;
  }

  @Override
  @NotNull
  public ResolvedPropertyModel url() {
    return GradlePropertyModelBuilder.create(myDslElement, URL).withDefault(myDefaultRepoUrl)
      .addTransform(new KtsOnlyPropertyTransform(new SingleArgumentMethodTransform("uri"))).buildResolved();
  }

  @NotNull
  @Override
  public RepositoryType getType() {
    String url = url().forceString();
    if (JCenterRepositoryModel.JCENTER_DEFAULT_REPO_URL.equals(url)) {
      return RepositoryType.JCENTER_DEFAULT;
    }
    else if (MavenCentralRepositoryModel.MAVEN_CENTRAL_DEFAULT_REPO_URL.equals(url)) {
      return RepositoryType.MAVEN_CENTRAL;
    }
    else if (GoogleDefaultRepositoryModel.GOOGLE_DEFAULT_REPO_URL.equals(url)) {
      return RepositoryType.GOOGLE_DEFAULT;
    }
    return RepositoryType.MAVEN;
  }
}
