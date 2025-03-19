// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.CalledInAny;
import org.jetbrains.annotations.NotNull;

import java.util.EventListener;

/**
 * Allows to decouple parts of {@link ChangeListManager} refresh into a separate process.
 * <p>
 * When specified, {@link ChangeProvider} is no longer expected to report ignored/unversioned files.
 * Instead, these should be loaded by this handler autonomously, and updates notified using {@link VcsManagedFilesHolderListener}.
 * Note, that holder will need to implement its own logic for tracking modifications,
 * as {@link VcsDirtyScopeManager} serves only {@link ChangeProvider}.
 * <p>
 * This allows to increase general responsiveness of {@link ChangeListManager}
 * if refresh of ignored/untracked files is a dramatically slower operation than refresh of modified files.
 *
 * @see ChangeListManagerImpl#notifyUnchangedFileStatusChanged
 */
public interface VcsManagedFilesHolder extends FilePathHolder {
  ProjectExtensionPointName<Provider> VCS_UNVERSIONED_FILES_HOLDER_EP
    = new ProjectExtensionPointName<>("com.intellij.vcs.unversionedFilesHolder");
  ProjectExtensionPointName<Provider> VCS_RESOLVED_CONFLICTS_FILES_HOLDER_EP
    = new ProjectExtensionPointName<>("com.intellij.vcs.resolvedConflictsFilesHolder");
  ProjectExtensionPointName<Provider> VCS_IGNORED_FILES_HOLDER_EP
    = new ProjectExtensionPointName<>("com.intellij.vcs.ignoredFilesHolder");

  @Topic.ProjectLevel
  Topic<VcsManagedFilesHolderListener> TOPIC = Topic.create("VcsManagedFilesHolder update", VcsManagedFilesHolderListener.class);

  /**
   * Whether data is dirty and there's an ongoing refresh.
   * <p>
   * Ex: it affects argument passed to {@link ChangeListListener#unchangedFileStatusChanged(boolean)}
   * and progress indicator in the tree.
   */
  default boolean isInUpdatingMode() { return false; }

  interface Provider {
    @NotNull
    AbstractVcs getVcs();

    @NotNull
    VcsManagedFilesHolder createHolder();
  }

  interface VcsManagedFilesHolderListener extends EventListener {
    /**
     * Notify that holder data was changed.
     */
    @CalledInAny
    void updatingModeChanged();
  }
}
