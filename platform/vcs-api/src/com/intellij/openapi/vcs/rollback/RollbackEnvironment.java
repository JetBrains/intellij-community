// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.rollback;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Interface for performing VCS rollback / revert operations.
 */
public interface RollbackEnvironment {
  /**
   * Returns the name of operation which is shown in the UI (in menu item name, dialog title and button text).
   *
   * @return the user-readable name of operation (for example, "Rollback" or "Revert").
   */
  @Nls(capitalization = Nls.Capitalization.Title)
  @NotNull
  String getRollbackOperationName();

  /**
   * Rolls back the specified changes.
   *
   * @param changes    the changes to roll back.
   * @param exceptions list of errors occurred during rollback
   */
  void rollbackChanges(List<? extends Change> changes, final List<VcsException> vcsExceptions, @NotNull final RollbackProgressListener listener);

  /**
   * Rolls back the deletion of files which have been deleted locally but not scheduled for deletion
   * from VCS. The implementation of this method should get the current version of the listed files from VCS.
   * You do not need to implement this method if you never report such files to
   * {@link com.intellij.openapi.vcs.changes.ChangelistBuilder#processLocallyDeletedFile}.
   *
   * @param files      the files to rollback deletion of.
   * @param exceptions list of errors occurred during rollback
   */
  void rollbackMissingFileDeletion(List<? extends FilePath> files, final List<? super VcsException> exceptions,
                                   final RollbackProgressListener listener);

  /**
   * Rolls back the modifications of files which have been made writable but not properly checked out from VCS.
   * You do not need to implement this method if you never report such files to
   * {@link com.intellij.openapi.vcs.changes.ChangelistBuilder#processModifiedWithoutCheckout}.
   *
   * @param files      the files to rollback.
   * @param exceptions list of errors occurred during rollback
   */
  void rollbackModifiedWithoutCheckout(List<? extends VirtualFile> files, final List<? super VcsException> exceptions,
                                       final RollbackProgressListener listener);

  /**
   * This is called when the user performs an undo that returns a file to a state in which it was
   * checked out or last saved. The implementation of this method can compare the current state of file
   * with the base revision and undo the checkout if the file is identical. Implementing this method is
   * optional.
   *
   * @param file the file to rollback.
   */
  void rollbackIfUnchanged(VirtualFile file);

  /**
   * @return the list of VCS-specific rollback-flavoured actions to show in Commit dialog
   */
  default Collection<? extends AnAction> createCustomRollbackActions() {
    return Collections.emptyList();
  }
}
