// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsKey;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Builder for the list of changes in the file system. The instances of
 * this class are used to collect changes that happened in the file system.
 *
 * @author max
 * @see ChangeProvider#getChanges(VcsDirtyScope, ChangelistBuilder, com.intellij.openapi.progress.ProgressIndicator, ChangeListManagerGate)
 */
public interface ChangelistBuilder {
  /**
   * Process a change to the file.
   * This method is used to report changes that the version control system knows about.
   *
   * @param change a change to process.
   * @param vcsKey VCS
   */
  void processChange(Change change, VcsKey vcsKey);

  void processChangeInList(Change change, @Nullable ChangeList changeList, VcsKey vcsKey);

  /**
   * Put the given change into the change list with the given name.
   * If there is no such change list, it is created.
   * This method allows not to refer to ChangeListManager for the LocalChangeList object.
   *
   * @param change         Submitted change
   * @param changeListName A name for a change list.
   * @param vcsKey         VCS
   */
  void processChangeInList(Change change, @NlsSafe String changeListName, VcsKey vcsKey);

  void removeRegisteredChangeFor(final FilePath path);

  /**
   * Process a file that is not under version control.
   *
   * @param file a file to process
   * @deprecated use {@link #processUnversionedFile(FilePath)} instead
   */
  @Deprecated
  default void processUnversionedFile(VirtualFile file) {
    if (file != null) {
      processUnversionedFile(VcsUtil.getFilePath(file));
    }
  }

  void processUnversionedFile(FilePath filePath);

  /**
   * Process a file that was deleted locally, but version
   * control has not been notified about removal yet.
   *
   * @param file a file to process
   */
  void processLocallyDeletedFile(FilePath file);

  void processLocallyDeletedFile(final LocallyDeletedChange locallyDeletedChange);

  /**
   * Process the file that was modified without explicit checkout
   * (if version control supports such behavior).
   *
   * @param file a file to process
   */
  void processModifiedWithoutCheckout(VirtualFile file);

  /**
   * Process the file that is ignored by the version control.
   *
   * @param file an ignored file
   * @deprecated use {@link #processIgnoredFile(FilePath)} instead
   */
  @Deprecated(forRemoval = true)
  default void processIgnoredFile(VirtualFile file) {
    if (file != null) {
      processIgnoredFile(VcsUtil.getFilePath(file));
    }
  }

  void processIgnoredFile(FilePath filePath);

  /**
   * technically locked folder (for Subversion: locked in working copy to keep WC's state consistent)
   */
  void processLockedFolder(VirtualFile file);

  /**
   * Logically locked file: (in repository) in lock-modify-unlock model
   */
  void processLogicallyLockedFolder(VirtualFile file, LogicalLock logicalLock);

  /**
   * Report a file which has been updated to a branch other than that of the files around it
   * ("switched"). Changed files (reported through {@link #processChange}) can also be reported as switched.
   *
   * @param file      the switched file
   * @param branch    the name of the branch to which the file is switched.
   * @param recursive if true, all subdirectories of the file are also marked as switched to that branch
   */
  void processSwitchedFile(VirtualFile file, @NlsSafe String branch, final boolean recursive);

  void processRootSwitch(VirtualFile file, @NlsSafe String branch);

  boolean reportChangesOutsideProject();

  void reportAdditionalInfo(@NlsContexts.Label final String text);

  void reportAdditionalInfo(final Factory<JComponent> infoComponent);
}
