/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.impl.CacheDiffRequestProcessor;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class CacheDiffRefreshableRequestProcessor<T> extends CacheDiffRequestProcessor<T> {
  public CacheDiffRefreshableRequestProcessor(@Nullable Project project) {
    super(project);
  }

  public CacheDiffRefreshableRequestProcessor(@Nullable Project project, @NotNull String place) {
    super(project, place);
  }

  /**
   * Notify currently shown diff that it's not needed now and cached values can be reset, a.e. before hiding preview panel
   */
  public abstract void clear();

  /**
   * Get newly requested element for diff and update/create new diff request for it
   * a.e. get selection from some model and check if previously shown diff request need to be replaced or still valid for such selection
   */
  @CalledInAwt
  public abstract void refresh();
}
