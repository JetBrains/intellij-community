// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListener;
import com.intellij.openapi.vcs.history.VcsHistoryCache;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Manages the version control systems used by a specific project.
 */
public abstract class ProjectLevelVcsManager {
  // project level
  public static final Topic<VcsListener> VCS_CONFIGURATION_CHANGED = new Topic<>(VcsListener.class, Topic.BroadcastDirection.NONE);
  /**
   * VCS configuration changed in VCS plugin. Project level.
   */
  public static final Topic<VcsListener> VCS_CONFIGURATION_CHANGED_IN_PLUGIN = new Topic<>(VcsListener.class, Topic.BroadcastDirection.NONE);

  public abstract void iterateVfUnderVcsRoot(VirtualFile file, Processor<? super VirtualFile> processor);

  /**
   * Returns the instance for the specified project.
   */
  public static ProjectLevelVcsManager getInstance(@NotNull Project project) {
    return project.getService(ProjectLevelVcsManager.class);
  }

  /**
   * Returns the list of all registered version control systems.
   */
  public abstract VcsDescriptor[] getAllVcss();

  /**
   * Returns the version control system with the specified name.
   *
   * @return the VCS instance, or {@code null} if none was found.
   */
  public abstract @Nullable AbstractVcs findVcsByName(@Nullable @NonNls String name);

  public abstract @Nullable VcsDescriptor getDescriptor(final String name);

  /**
   * Checks if all given files are managed by the specified VCS.
   */
  public abstract boolean checkAllFilesAreUnder(AbstractVcs abstractVcs, VirtualFile[] files);

  /**
   * Returns the VCS managing the specified file.

   * @return the VCS instance, or {@code null} if the file does not belong to any module or the module
   *         it belongs to is not under version control.
   */
  public abstract @Nullable AbstractVcs getVcsFor(@NotNull VirtualFile file);

  /**
   * Returns the VCS managing the specified file path.
   *
   * @return the VCS instance, or {@code null} if the file does not belong to any module or the module
   *         it belongs to is not under version control.
   */
  public abstract @Nullable AbstractVcs getVcsFor(FilePath file);

  /**
   * Return the parent directory of the specified file which is mapped to a VCS.
   *
   * @return the root, or {@code null} if the specified file is not in a VCS-managed directory.
   */
  public abstract @Nullable VirtualFile getVcsRootFor(@Nullable VirtualFile file);

  /**
   * Return the parent directory of the specified file path which is mapped to a VCS.
   *
   * @return the root, or {@code null} if the specified file is not in a VCS-managed directory.
   */
  public abstract @Nullable VirtualFile getVcsRootFor(FilePath file);

  public abstract @Nullable VcsRoot getVcsRootObjectFor(final VirtualFile file);

  public abstract @Nullable VcsRoot getVcsRootObjectFor(FilePath file);

  /**
   * Checks if the specified VCS is used by any of the modules in the project.
   */
  public abstract boolean checkVcsIsActive(AbstractVcs vcs);

  /**
   * Checks if the VCS with the specified name is used by any of the modules in the project.
   */
  public abstract boolean checkVcsIsActive(@NonNls String vcsName);

  /**
   * Returns the list of VCSes used by at least one module in the project.
   */
  public abstract AbstractVcs @NotNull [] getAllActiveVcss();

  public abstract @Nullable AbstractVcs getSingleVCS();

  public abstract boolean hasActiveVcss();

  public abstract boolean hasAnyMappings();

  /**
   * @deprecated use {@link #addMessageToConsoleWindow(String, ConsoleViewContentType)}
   */
  @Deprecated
  public abstract void addMessageToConsoleWindow(String message, TextAttributes attributes);

  public abstract void addMessageToConsoleWindow(@Nullable String message, @NotNull ConsoleViewContentType contentType);

  public abstract void addMessageToConsoleWindow(@Nullable VcsConsoleLine line);

  public abstract @NotNull VcsShowSettingOption getStandardOption(@NotNull VcsConfiguration.StandardOption option,
                                                                  @NotNull AbstractVcs vcs);

  public abstract @NotNull VcsShowConfirmationOption getStandardConfirmation(@NotNull VcsConfiguration.StandardConfirmation option,
                                                                             AbstractVcs vcs);

  public abstract @NotNull VcsShowSettingOption getOrCreateCustomOption(@NotNull String vcsActionName,
                                                                        @NotNull AbstractVcs vcs);

  @CalledInAwt
  public abstract void showProjectOperationInfo(final UpdatedFiles updatedFiles, String displayActionName);

  /**
   * Adds a listener for receiving notifications about changes in VCS configuration for the project.
   *
   * @deprecated use {@link #VCS_CONFIGURATION_CHANGED} instead
   */
  @Deprecated
  public abstract void addVcsListener(VcsListener listener);

  /**
   * Removes a listener for receiving notifications about changes in VCS configuration for the project.
   *
   * @deprecated use {@link #VCS_CONFIGURATION_CHANGED} instead
   */
  @Deprecated
  public abstract void removeVcsListener(VcsListener listener);

  /**
   * Marks the beginning of a background VCS operation (commit or update).
   */
  public abstract void startBackgroundVcsOperation();

  /**
   * Marks the end of a background VCS operation (commit or update).
   */
  public abstract void stopBackgroundVcsOperation();

  /**
   * Checks if a background VCS operation (commit or update) is currently in progress.
   */
  public abstract boolean isBackgroundVcsOperationRunning();

  public abstract List<VirtualFile> getRootsUnderVcsWithoutFiltering(final AbstractVcs vcs);

  public abstract VirtualFile[] getRootsUnderVcs(@NotNull AbstractVcs vcs);

  /**
   * Also includes into list all modules under roots
   */
  public abstract List<VirtualFile> getDetailedVcsMappings(final AbstractVcs vcs);

  public abstract VirtualFile[] getAllVersionedRoots();

  public abstract VcsRoot @NotNull [] getAllVcsRoots();

  public abstract String getConsolidatedVcsName();

  /**
   * @deprecated Use just {@link #setDirectoryMappings(List)}.
   */
  @Deprecated
  public void updateActiveVcss() {}

  public abstract List<VcsDirectoryMapping> getDirectoryMappings();
  public abstract List<VcsDirectoryMapping> getDirectoryMappings(AbstractVcs vcs);

  public abstract @Nullable VcsDirectoryMapping getDirectoryMappingFor(FilePath path);

  /**
   * This method can be used only when initially loading the project configuration!
   */
  @Deprecated
  public abstract void setDirectoryMapping(final String path, final String activeVcsName);

  public abstract void setDirectoryMappings(final List<VcsDirectoryMapping> items);

  public abstract void iterateVcsRoot(final VirtualFile root, final Processor<? super FilePath> iterator);

  public abstract void iterateVcsRoot(final VirtualFile root, final Processor<? super FilePath> iterator,
                                      @Nullable VirtualFileFilter directoryFilter);

  public abstract @Nullable AbstractVcs findVersioningVcs(VirtualFile file);

  public abstract @NotNull VcsRootChecker getRootChecker(@NotNull AbstractVcs vcs);

  public abstract CheckoutProvider.Listener getCompositeCheckoutListener();

  public abstract VcsHistoryCache getVcsHistoryCache();
  public abstract ContentRevisionCache getContentRevisionCache();
  public abstract boolean isFileInContent(final VirtualFile vf);
  public abstract boolean isIgnored(@NotNull VirtualFile vf);
  public abstract boolean isIgnored(@NotNull FilePath filePath);

  public abstract @NotNull VcsAnnotationLocalChangesListener getAnnotationLocalChangesListener();

  /**
   * Shows VCS console.
   * <p>
   * Does nothing if {@code vcs.showConsole} turned off.
   */
  @CalledInAwt
  public abstract void showConsole();

  /**
   * Shows VCS console and then performs the given command.
   * <p>
   * Does nothing if {@code vcs.showConsole} turned off.
   */
  @CalledInAwt
  public abstract void showConsole(@Nullable Runnable then);

  /**
   * Navigates to the end in VCS console.
   */
  @CalledInAwt
  public abstract void scrollConsoleToTheEnd();

  /**
   * Executes task on pooled thread, delayed until core vcs services are initialized.
   */
  public abstract void runAfterInitialization(@NotNull Runnable runnable);

  /**
   * Checks whether VCS console is enabled and VCS tool window exists.
   */
  public abstract boolean isConsoleVisible();
}
