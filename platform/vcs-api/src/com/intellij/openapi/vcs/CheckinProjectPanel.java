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
package com.intellij.openapi.vcs;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.ui.Refreshable;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.io.File;
import java.util.Collection;

/**
 * Interface for working with the checkin dialog user interface (retrieving the files
 * included in the checkin operation, getting/setting the commit message and so on).
 * The active check-in dialog can be retrieved from the using {@link Refreshable#PANEL_KEY}
 *
 * @see com.intellij.openapi.vcs.checkin.CheckinHandlerFactory#createHandler(CheckinProjectPanel)
 */
public interface CheckinProjectPanel extends Refreshable {
  JComponent getComponent();

  JComponent getPreferredFocusedComponent();

  /**
   * Checks if the checkin operation has anything to check in.
   *
   * @return true if any files need to be updated, added or deleted; false otherwise.
   */
  boolean hasDiffs();

  /**
   * Returns the list of files selected for checkin, as {@link VirtualFile} objects. The returned list
   * does not include files which will be deleted from the VCS during the check-in operation.
   *
   * @return the files selected for checkin.
   */
  Collection<VirtualFile> getVirtualFiles();

  Collection<Change> getSelectedChanges();

  /**
   * Returns the list of files selected for checkin, as {@link java.io.File} objects. The returned list
   * includes files which will be deleted from the VCS during the check-in operation.
   *
   * @return the files selected for checkin.
   * @since 5.1
   */
  Collection<File> getFiles();

  /**
   * Returns the project in which the checkin is performed.
   *
   * @return the project instance.
   */
  Project getProject();

  /**
   * Checks whether any changes under vcs are included into commit operation
   */
  boolean vcsIsAffected(final String name);

  /**
   * Returns the list of roots for the check-in (roots of all modules under version control
   * in a "checkin project" operation, the files/directories selected for check-in in a
   * "checkin directory" or "checkin file" operation).
   *
   * @return the list of roots for check-in.
   */
  Collection<VirtualFile> getRoots();

  /**
   * Sets the description for the check-in.
   *
   * @param currentDescription the description text.
   */
  void setCommitMessage(final String currentDescription);

  void setWarning(final String s);

  /**
   * Gets the description for the check-in.
   *
   * @return the description text.
   * @since 5.1
   */
  String getCommitMessage();

  String getCommitActionName();
}
