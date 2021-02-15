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
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.model.ext.PropertyUtil.isPropertiesElementOrMap;

/**
 * Represents a repository defined with mavenCentral().
 */
public class MavenCentralRepositoryModel extends UrlBasedRepositoryModelImpl {
  @NonNls public static final String MAVEN_CENTRAL_METHOD_NAME = "mavenCentral";
  @NonNls public static final String MAVEN_CENTRAL_DEFAULT_REPO_URL = "https://repo1.maven.org/maven2/";

  public MavenCentralRepositoryModel(@NotNull GradlePropertiesDslElement holder, @NotNull GradlePropertiesDslElement dslElement) {
    super(holder, dslElement, "MavenRepo", MAVEN_CENTRAL_DEFAULT_REPO_URL);
  }

  @NotNull
  public ResolvedPropertyModel artifactUrls() {
    if (isPropertiesElementOrMap(myDslElement)) {
      return GradlePropertyModelBuilder.create(myDslElement, ARTIFACT_URLS).buildResolved();
    }
    else {
      return GradlePropertyModelBuilder.create(myDslElement).buildResolved();
    }
  }
}