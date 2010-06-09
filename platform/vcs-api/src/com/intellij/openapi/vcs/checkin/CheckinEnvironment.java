/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.checkin;

import com.intellij.openapi.vcs.CheckinProjectPanel;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsProviderMarker;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeList;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.NullableFunction;
import com.intellij.util.PairConsumer;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Interface for performing VCS checkin / commit / submit operations.
 *
 * @author lesya
 * @see com.intellij.openapi.vcs.AbstractVcs#getCheckinEnvironment()
 */
public interface CheckinEnvironment extends VcsProviderMarker {
  @Nullable
  RefreshableOnComponent createAdditionalOptionsPanel(CheckinProjectPanel panel, PairConsumer<Object, Object> additionalDataConsumer);

  @Nullable
  String getDefaultMessageFor(FilePath[] filesToCheckin);

  @Nullable
  @NonNls
  String getHelpId();

  String getCheckinOperationName();

  @Nullable
  List<VcsException> commit(List<Change> changes, String preparedComment);

  @Nullable
  List<VcsException> commit(List<Change> changes, String preparedComment, @NotNull NullableFunction<Object, Object> parametersHolder);

  @Nullable
  List<VcsException> scheduleMissingFileForDeletion(List<FilePath> files);

  @Nullable
  List<VcsException> scheduleUnversionedFilesForAddition(List<VirtualFile> files);

  boolean keepChangeListAfterCommit(ChangeList changeList);
}
