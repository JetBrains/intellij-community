/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.android.maven;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.module.Module;
import org.jetbrains.android.facet.AndroidFacetConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public interface AndroidMavenProvider {
  ExtensionPointName<AndroidMavenProvider> EP_NAME = ExtensionPointName.create("org.jetbrains.android.mavenProvider");

  boolean isMavenizedModule(@NotNull Module module);

  @NotNull
  List<File> getMavenDependencyArtifactFiles(@NotNull Module module);

  @Nullable
  String getBuildDirectory(@NotNull Module module);

  void setPathsToDefault(@NotNull Module module, AndroidFacetConfiguration facetConfiguration);
}
