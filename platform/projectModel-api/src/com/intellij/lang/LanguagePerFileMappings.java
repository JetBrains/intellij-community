/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public abstract class LanguagePerFileMappings<T> extends PerFileMappingsBase<T> implements PerFileMappings<T> {

  private final Project myProject;

  public LanguagePerFileMappings(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  protected Project getProject() {
    return myProject;
  }

  @NotNull
  protected String getValueAttribute() {
    return "dialect";
  }

}
