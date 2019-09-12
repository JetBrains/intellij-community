// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProcessCanceledException;
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

  public static final Topic<VcsListener> VCS_CONFIGURATION_CHANGED = Topic.create("VCS configuration changed", VcsListener.class);
  public static final Topic<VcsListener> VCS_CONFIGURATION_CHANGED_IN_PLUGIN = Topic.create("VCS configuration changed in VCS plugin", VcsListener.class);

  public abstract void iterateVfUnderVcsRoot(VirtualFile file, Processor<? super VirtualFile> processor);

  /**
   * Returns the instance for the specified project.
   */
  public static ProjectLevelVcsManager getInstance(Project project) {
    return project.getService(ProjectLevelVcsManager.class);
  }

  /**
   * Gets the instance of the component if the project wasn't disposed. If the project was
   * disposed, throws ProcessCanceledException. Should only be used for calling from background
   * threads (for example, committed changes refresh thread).
   */
  public static ProjectLevelVcsManager getInstanceChecked(final Project project) {
    return ReadAction.compute(() -> {
      if (project.isDisposed()) throw new ProcessCanceledException();
      return getInstance(project);
    });
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
  @Nullable
  public abstract AbstractVcs findVcsByName(@NonNls String name);

  @Nullable
  public abstract VcsDescriptor getDescriptor(final String name);

  /**
   * Checks if all given files are managed by the specified VCS.
   */
  public abstract boolean checkAllFilesAreUnder(AbstractVcs abstractVcs, VirtualFile[] files);

  /**
   * Returns the VCS managing the specified file.

   * @return the VCS instance, or {@code null} if the file does not belong to any module or the module
   *         it belongs to is not under version control.
   */
  @Nullable
  public abstract AbstractVcs getVcsFor(@NotNull VirtualFile file);

  /**
   * Returns the VCS managing the specified file path.
   *
   * @return the VCS instance, or {@code null} if the file does not belong to any module or the module
   *         it belongs to is not under version control.
   */
  @Nullable
  public abstract AbstractVcs getVcsFor(FilePath file);

  /**
   * Return the parent directory of the specified file which is mapped to a VCS.
   *
   * @return the root, or {@code null} if the specified file is not in a VCS-managed directory.
   */
  @Nullable
  public abstract VirtualFile getVcsRootFor(@Nullable VirtualFile file);

  /**
   * Return the parent directory of the specified file path which is mapped to a VCS.
   *
   * @return the root, or {@code null} if the specified file is not in a VCS-managed directory.
   */
  @Nullable
  public abstract VirtualFile getVcsRootFor(FilePath file);

  @Nullable
  public abstract VcsRoot getVcsRootObjectFor(final VirtualFile file);

  @Nullable
  public abstract VcsRoot getVcsRootObjectFor(FilePath file);

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
  @NotNull
  public abstract AbstractVcs[] getAllActiveVcss();

  public abstract boolean hasActiveVcss();

  public abstract boolean hasAnyMappings();

  /**
   * @deprecated use {@link #addMessageToConsoleWindow(String, ConsoleViewContentType)}
   */
  @Deprecated
  public abstract void addMessageToConsoleWindow(String message, TextAttributes attributes);

  public abstract void addMessageToConsoleWindow(@Nullable String message, @NotNull ConsoleViewContentType contentType);

  @NotNull
  public abstract VcsShowSettingOption getStandardOption(@NotNull VcsConfiguration.StandardOption option,
                                                         @NotNull AbstractVcs vcs);

  @NotNull
  public abstract VcsShowConfirmationOption getStandardConfirmation(@NotNull VcsConfiguration.StandardConfirmation option,
                                                                    AbstractVcs vcs);

  @NotNull
  public abstract VcsShowSettingOption getOrCreateCustomOption(@NotNull String vcsActionName,
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

  @NotNull
  public abstract VcsRoot[] getAllVcsRoots();

  /**
   * @deprecated Use just {@link #setDirectoryMappings(List)}.
   */
  @Deprecated
  public void updateActiveVcss() {}

  public abstract List<VcsDirectoryMapping> getDirectoryMappings();
  public abstract List<VcsDirectoryMapping> getDirectoryMappings(AbstractVcs vcs);

  @Nullable
  public abstract VcsDirectoryMapping getDirectoryMappingFor(FilePath path);

  /**
   * This method can be used only when initially loading the project configuration!
   */
  @Deprecated
  public abstract void setDirectoryMapping(final String path, final String activeVcsName);

  public abstract void setDirectoryMappings(final List<VcsDirectoryMapping> items);

  public abstract void iterateVcsRoot(final VirtualFile root, final Processor<? super FilePath> iterator);

  public abstract void iterateVcsRoot(final VirtualFile root, final Processor<? super FilePath> iterator,
                                      @Nullable VirtualFileFilter directoryFilter);

  @Nullable
  public abstract AbstractVcs findVersioningVcs(VirtualFile file);

  @NotNull
  public abstract VcsRootChecker getRootChecker(@NotNull AbstractVcs vcs);

  public abstract CheckoutProvider.Listener getCompositeCheckoutListener();

  public abstract VcsHistoryCache getVcsHistoryCache();
  public abstract ContentRevisionCache getContentRevisionCache();
  public abstract boolean isFileInContent(final VirtualFile vf);
  public abstract boolean isIgnored(@NotNull VirtualFile vf);
  public abstract boolean isIgnored(@NotNull FilePath filePath);

  @NotNull
  public abstract VcsAnnotationLocalChangesListener getAnnotationLocalChangesListener();
}
