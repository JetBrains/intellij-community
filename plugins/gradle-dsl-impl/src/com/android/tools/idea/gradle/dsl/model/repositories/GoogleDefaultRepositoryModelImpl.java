/*
 * Copyright (C) 2017 The Android Open Source Project
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

import com.android.tools.idea.gradle.dsl.api.repositories.GoogleDefaultRepositoryModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel.RepositoryType.GOOGLE_DEFAULT;

/**
 * Represents a repository defined with google().
 */
public class GoogleDefaultRepositoryModelImpl extends UrlBasedRepositoryModelImpl implements GoogleDefaultRepositoryModel {

  public GoogleDefaultRepositoryModelImpl(@NotNull GradlePropertiesDslElement holder, @NotNull GradlePropertiesDslElement element) {
    super(holder, element, GOOGLE_DEFAULT_REPO_NAME, GOOGLE_DEFAULT_REPO_URL);
  }
}
