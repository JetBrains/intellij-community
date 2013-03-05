/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.autoimport;

import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 3/4/13 12:24 PM
 */
public class GradleModuleDependencyExportedChange
  extends AbstractGradleDependencyExportedUserChange<GradleModuleDependencyExportedChange>
{

  @SuppressWarnings("UnusedDeclaration")
  public GradleModuleDependencyExportedChange() {
    // Required for IJ serialization
  }

  public GradleModuleDependencyExportedChange(@NotNull String moduleName, @NotNull String dependencyName, boolean exported) {
    super(moduleName, dependencyName, exported);
  }

  @Override
  public void invite(@NotNull GradleUserProjectChangeVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public String toString() {
    return String.format("module dependency '%s' is %sexported at module '%s'",
                         getDependencyName(), isExported() ? "" : "non-", getModuleName());
  }
}
