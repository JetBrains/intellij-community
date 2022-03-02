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

import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Can be used to override default diff views for specific {@link Change}.
 */
public interface ChangeDiffRequestProvider {
  ExtensionPointName<ChangeDiffRequestProvider> EP_NAME =
    ExtensionPointName.create("com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProvider");

  /**
   * Specifies if two changes will produce different DiffRequests.
   * For example, it can be used after 'Local Changes' refresh: if Change before and after are equal, old request may be reused.
   * <p>
   * Return {@link ThreeState#UNSURE} for unknown inputs.
   *
   * @see DiffRequestProducer equality javadoc
   */
  @NotNull
  ThreeState isEquals(@NotNull Change change1, @NotNull Change change2);

  /**
   * @return if provider should be used for specific change
   */
  boolean canCreate(@Nullable Project project, @NotNull Change change);

  /**
   * Same as {@link DiffRequestProducer#process}.
   *
   * @see ChangeDiffRequestProducer#getChange()
   */
  @NotNull
  DiffRequest process(@NotNull ChangeDiffRequestProducer presentable,
                      @NotNull UserDataHolder context,
                      @NotNull ProgressIndicator indicator) throws ProcessCanceledException, DiffRequestProducerException;
}
