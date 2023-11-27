// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs;

import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.changes.VcsAnnotationLocalChangesListener;
import com.intellij.openapi.vcs.history.VcsHistoryCache;
import com.intellij.openapi.vcs.impl.ContentRevisionCache;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Processor;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Manages the version control systems used by a specific project.
 */
public abstract class ProjectLevelVcsManager {
  /**
   * Fired when {@link #getVcsFor(VirtualFile)} and similar methods change their value.
   */
  @Topic.ProjectLevel
  public static final Topic<VcsMappingListener> VCS_CONFIGURATION_CHANGED =
    new Topic<>(VcsMappingListener.class, Topic.BroadcastDirection.NONE);

  /**
   * This event is only fired by SVN plugin.
   * <p>
   * Typically, it can be ignored unless plugin supports SVN and uses
   * {@link #getRootsUnderVcs(AbstractVcs)}, {@link #getAllVcsRoots} or {@link #getAllVersionedRoots()} methods.
   * <p>
   * See {@link org.jetbrains.idea.svn.SvnFileUrlMappingImpl} and {@link AbstractVcs#getCustomConvertor}.
   */
  @Topic.ProjectLevel
  public static final Topic<PluginVcsMappingListener> VCS_CONFIGURATION_CHANGED_IN_PLUGIN =
    new Topic<>(PluginVcsMappingListener.class, Topic.BroadcastDirection.NONE);

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

  public abstract @Nullable VcsDescriptor getDescriptor(@NonNls String name);

  /**
   * Checks if all given files are managed by the specified VCS.
   */
  public abstract boolean checkAllFilesAreUnder(AbstractVcs abstractVcs, VirtualFile[] files);

  public abstract @NotNull @NlsSafe String getShortNameForVcsRoot(@NotNull VirtualFile file);

  /**
   * Returns the VCS managing the specified file.
   *
   * @return the VCS instance, or {@code null} if the file does not belong to any module or the module
   * it belongs to is not under version control.
   */
  public abstract @Nullable AbstractVcs getVcsFor(@Nullable VirtualFile file);

  /**
   * Returns the VCS managing the specified file path.
   *
   * @return the VCS instance, or {@code null} if the file does not belong to any module or the module
   * it belongs to is not under version control.
   */
  public abstract @Nullable AbstractVcs getVcsFor(@Nullable FilePath file);

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
  public abstract @Nullable VirtualFile getVcsRootFor(@Nullable FilePath file);

  public abstract @Nullable VcsRoot getVcsRootObjectFor(@Nullable VirtualFile file);

  public abstract @Nullable VcsRoot getVcsRootObjectFor(@Nullable FilePath file);

  /**
   * Checks if the specified VCS is used by any of the modules in the project.
   */
  public abstract boolean checkVcsIsActive(AbstractVcs vcs);

  /**
   * Checks if the VCS with the specified name is used by any of the modules in the project.
   */
  public abstract boolean checkVcsIsActive(@NonNls String vcsName);

  /**
   * Returns the list of VCSes supported by plugins.
   */
  public abstract AbstractVcs @NotNull [] getAllSupportedVcss();

  /**
   * Returns the list of VCSes used by at least one module in the project.
   */
  public abstract AbstractVcs @NotNull [] getAllActiveVcss();

  /**
   * @return VCS configured for the project, if there's only a single one. Return 'null' otherwise.
   */
  public abstract @Nullable AbstractVcs getSingleVCS();

  public abstract boolean hasActiveVcss();

  public abstract boolean hasAnyMappings();

  /**
   * @deprecated use {@link #addMessageToConsoleWindow(String, ConsoleViewContentType)}
   */
  @Deprecated(forRemoval = true)
  public abstract void addMessageToConsoleWindow(@Nls String message, TextAttributes attributes);

  /**
   * @see com.intellij.vcs.console.VcsConsoleTabService
   */
  public abstract void addMessageToConsoleWindow(@Nls @Nullable String message, @NotNull ConsoleViewContentType contentType);

  /**
   * @see com.intellij.vcs.console.VcsConsoleTabService
   */
  public abstract void addMessageToConsoleWindow(@Nullable VcsConsoleLine line);

  public abstract @NotNull VcsShowSettingOption getStandardOption(@NotNull VcsConfiguration.StandardOption option,
                                                                  @NotNull AbstractVcs vcs);

  public abstract @NotNull VcsShowConfirmationOption getStandardConfirmation(@NotNull VcsConfiguration.StandardConfirmation option,
                                                                             AbstractVcs vcs);

  @RequiresEdt
  public abstract void showProjectOperationInfo(final UpdatedFiles updatedFiles, @Nls String displayActionName);

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
   *
   * @deprecated To be removed
   */
  @Deprecated(forRemoval = true)
  public abstract List<VirtualFile> getDetailedVcsMappings(final AbstractVcs vcs);

  public abstract VirtualFile[] getAllVersionedRoots();

  public abstract VcsRoot @NotNull [] getAllVcsRoots();

  @Nls
  public abstract String getConsolidatedVcsName();

  /**
   * @deprecated Use just {@link #setDirectoryMappings(List)}.
   */
  @Deprecated(forRemoval = true)
  public void updateActiveVcss() { }

  public abstract List<VcsDirectoryMapping> getDirectoryMappings();

  public abstract List<VcsDirectoryMapping> getDirectoryMappings(AbstractVcs vcs);

  public abstract @Nullable VcsDirectoryMapping getDirectoryMappingFor(@Nullable FilePath path);

  /**
   * This method can be used only when initially loading the project configuration!
   */
  @Deprecated(forRemoval = true)
  public abstract void setDirectoryMapping(@NonNls String path, @NonNls String activeVcsName);

  public abstract void setDirectoryMappings(final List<VcsDirectoryMapping> items);

  public abstract void iterateVcsRoot(final VirtualFile root, final Processor<? super FilePath> iterator);

  public abstract void iterateVcsRoot(final VirtualFile root, final Processor<? super FilePath> iterator,
                                      @Nullable VirtualFileFilter directoryFilter);

  public abstract @Nullable AbstractVcs findVersioningVcs(@NotNull VirtualFile file);

  public abstract @NotNull VcsRootChecker getRootChecker(@NotNull AbstractVcs vcs);

  public abstract CheckoutProvider.Listener getCompositeCheckoutListener();

  public abstract VcsHistoryCache getVcsHistoryCache();

  public abstract ContentRevisionCache getContentRevisionCache();

  public abstract boolean isFileInContent(final VirtualFile vf);

  public abstract boolean isIgnored(@NotNull VirtualFile vf);

  public abstract boolean isIgnored(@NotNull FilePath filePath);

  public abstract @NotNull VcsAnnotationLocalChangesListener getAnnotationLocalChangesListener();

  /**
   * @deprecated Use {@link com.intellij.vcs.console.VcsConsoleTabService}
   */
  @RequiresEdt
  @Deprecated
  public abstract void showConsole();

  /**
   * @deprecated Use {@link com.intellij.vcs.console.VcsConsoleTabService}
   */
  @RequiresEdt
  @Deprecated
  public abstract void showConsole(@Nullable Runnable then);

  /**
   * @deprecated Use {@link com.intellij.vcs.console.VcsConsoleTabService}
   */
  @RequiresEdt
  @Deprecated
  public abstract void scrollConsoleToTheEnd();

  /**
   * @deprecated Use {@link com.intellij.vcs.console.VcsConsoleTabService}
   */
  @RequiresEdt
  @Deprecated
  public abstract boolean isConsoleVisible();

  /**
   * Execute the task on pooled thread, delayed until core vcs services are initialized.
   */
  public abstract void runAfterInitialization(@NotNull Runnable runnable);

  /**
   * Whether vcs mappings were already processed after opening the project.
   * ie: if true, one can assume that {@link #hasActiveVcss()} and {@link #hasAnyMappings()} match if the mappings are correct.
   * <p>
   * See {@link com.intellij.openapi.vcs.ex.ProjectLevelVcsManagerEx#VCS_ACTIVATED} listener that will be notified when this value changes.
   */
  public boolean areVcsesActivated() {
    return false;
  }
}
