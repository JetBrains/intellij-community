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
package com.intellij.openapi.vcs.history;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.vcs.CalledInBackground;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsProviderMarker;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface VcsHistoryProvider extends VcsProviderMarker {

  VcsDependentHistoryComponents getUICustomization(final VcsHistorySession session, final JComponent forShortcutRegistration);

  AnAction[] getAdditionalActions(final Runnable refresher);

  /**
   * Returns whether the history provider submits the custom-formatted date
   * and the standard "Date" column must be omitted to get rid of confusion.
   * @return true if history provider submits the custom-formatted date.
   */
  boolean isDateOmittable();

  @Nullable
  @NonNls
  String getHelpId();

  /**
   * Returns the history session for the specified file path.
   *
   * @param filePath the file path for which the session is requested.
   * @return the session, or null if the initial revisions loading process was cancelled.
   * @throws VcsException if an error occurred when loading the revisions
   */
  @Nullable
  @CalledInBackground
  VcsHistorySession createSessionFor(FilePath filePath) throws VcsException;

  void reportAppendableHistory(final FilePath path, final VcsAppendableHistorySessionPartner partner) throws VcsException;

  boolean supportsHistoryForDirectories();
}
