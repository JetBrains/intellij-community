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
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for all the repository models.
 */
public abstract class RepositoryModelImpl implements RepositoryModel {
  // TODO(xof): most other models have renamed properties, having the model-internal name being essentially arbitrary rather than a
  //  reflection of the external syntax.  However, because of the multiple ways of spelling some repositories, in particular map forms of
  //  `flatDir` and `mavenCentral`, we must for now preserve this as being exactly "name" rather than e.g. "mName"
  @NonNls public static final String NAME = "name";

  @NotNull private final String myDefaultRepoName;

  @NotNull protected final GradlePropertiesDslElement myHolder;
  @NotNull protected final GradlePropertiesDslElement myDslElement;

  protected RepositoryModelImpl(@NotNull GradlePropertiesDslElement holder,
                                @NotNull GradlePropertiesDslElement dslElement,
                                @NotNull String defaultRepoName) {
    myHolder = holder;
    myDslElement = dslElement;
    myDefaultRepoName = defaultRepoName;
  }

  @NotNull
  @Override
  public ResolvedPropertyModel name() {
    return GradlePropertyModelBuilder.create(myDslElement, NAME).withDefault(myDefaultRepoName).buildResolved();
  }
}
