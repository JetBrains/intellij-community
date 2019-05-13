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
package com.intellij.openapi.vcs;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Konstantin Bulenkov
 */
public abstract class CheckoutProviderEx implements CheckoutProvider {
  /**
   * @return a short unique identifier such as git, hg, svn, and so on
   */
  @NotNull
  public abstract String getVcsId();

  /**
   * Overloads CheckoutProvider#doCheckout(Project, Listener) to provide predefined repository URL
   */
  public abstract void doCheckout(@NotNull final Project project, @Nullable Listener listener, @Nullable String predefinedRepositoryUrl);
}
