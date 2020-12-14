/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api.configurations;

import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Model for the configurations given within Gradle build files. This DOES NOT include configurations pulled in by other plugins
 * e.g compile, api, test, implementation, unless they are explictly mentioned in the configurations block.
 */
public interface ConfigurationsModel extends GradleDslModel {
  @NotNull
  List<ConfigurationModel> all();

  @NotNull
  ConfigurationModel addConfiguration(@NotNull String name);

  void removeConfiguration(@NotNull String name);
}
