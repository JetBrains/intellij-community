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
package com.intellij.openapi.vcs.changes.actions.diff;

import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.impl.DiffViewerWrapper;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * WARNING: This is not an extension point you are looking for.
 * <p/>
 * Please, consider using {@link com.intellij.diff.DiffTool}, {@link com.intellij.diff.DiffExtension}, {@link ChangeDiffRequestProvider}
 * or introducing a better designed extension point into the platform, rather than adding a second usage of this one.
 */
@ApiStatus.Internal
public interface ChangeDiffViewerWrapperProvider {
  ExtensionPointName<ChangeDiffViewerWrapperProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffViewerWrapperProvider");

  @NotNull
  ThreeState isEquals(@NotNull Change change1, @NotNull Change change2);

  boolean canCreate(@Nullable Project project, @NotNull Change change);

  @NotNull
  DiffViewerWrapper process(@NotNull ChangeDiffRequestProducer presentable,
                            @NotNull UserDataHolder context,
                            @NotNull ProgressIndicator indicator) throws DiffRequestProducerException, ProcessCanceledException;
}
