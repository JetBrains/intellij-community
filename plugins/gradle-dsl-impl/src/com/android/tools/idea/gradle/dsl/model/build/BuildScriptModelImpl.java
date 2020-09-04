/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.build;

import com.android.tools.idea.gradle.dsl.api.BuildScriptModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.api.ext.ExtModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModelImpl;
import com.android.tools.idea.gradle.dsl.model.ext.ExtModelImpl;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoriesModelImpl;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.android.tools.idea.gradle.dsl.parser.build.BuildScriptDslElement;
import com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import static com.android.tools.idea.gradle.dsl.parser.dependencies.DependenciesDslElement.DEPENDENCIES;
import static com.android.tools.idea.gradle.dsl.parser.ext.ExtDslElement.EXT;
import static com.android.tools.idea.gradle.dsl.parser.repositories.RepositoriesDslElement.REPOSITORIES;

public class BuildScriptModelImpl extends GradleDslBlockModel implements BuildScriptModel {

  public BuildScriptModelImpl(@NotNull BuildScriptDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  @Override
  public DependenciesModel dependencies() {
    DependenciesDslElement dependenciesDslElement = myDslElement.ensurePropertyElement(DEPENDENCIES);
    return new DependenciesModelImpl(dependenciesDslElement);
  }

  @NotNull
  @Override
  public RepositoriesModel repositories() {
    RepositoriesDslElement repositoriesDslElement = myDslElement.ensurePropertyElement(REPOSITORIES);
    return new RepositoriesModelImpl(repositoriesDslElement);
  }

  /**
   * Removes property {@link RepositoriesDslElement#REPOSITORIES}.
   */
  @Override
  @TestOnly
  public void removeRepositoriesBlocks() {
    myDslElement.removeProperty(REPOSITORIES.name);
  }

  @NotNull
  @Override
  public ExtModel ext() {
    int at = 0;
    List<GradleDslElement> elements = myDslElement.getAllElements();
    if (!elements.isEmpty() && elements.get(0) instanceof ApplyDslElement) {
      at += 1;
    }
    ExtDslElement extDslElement = myDslElement.ensurePropertyElementAt(EXT, at);
    return new ExtModelImpl(extDslElement);
  }

  @NotNull
  @Override
  public Map<String, GradlePropertyModel> getInScopeProperties() {
    return super.getDeclaredProperties().stream().collect(Collectors.toMap(e -> e.getName(), e -> e));
  }
}
