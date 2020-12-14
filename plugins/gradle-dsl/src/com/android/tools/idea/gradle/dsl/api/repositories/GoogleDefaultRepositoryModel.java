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
package com.android.tools.idea.gradle.dsl.api.repositories;

import org.jetbrains.annotations.NonNls;

public interface GoogleDefaultRepositoryModel extends UrlBasedRepositoryModel {
  @NonNls String GOOGLE_METHOD_NAME = "google";
  @NonNls String GOOGLE_DEFAULT_REPO_NAME = "Google";
  @NonNls String GOOGLE_DEFAULT_REPO_URL = "https://maven.google.com/";
}
