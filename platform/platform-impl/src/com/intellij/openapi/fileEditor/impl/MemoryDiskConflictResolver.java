// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.impl;

import com.intellij.diff.comparison.MergeResolveUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManager.ConflictResolution;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectLocator;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@ApiStatus.Internal
public class MemoryDiskConflictResolver {
  private static final Logger LOG = Logger.getInstance(MemoryDiskConflictResolver.class);

  /**
   * The default comes with the call, because the common text is collected as early as the first document load,
   * and the registry is not ready then.
   */
  static boolean isMergeEnabled() {
    return Registry.is("ide.merge.external.changes", true);
  }

  private final List<ConflictResolutionOverride> myConflictResolutionOverrides = ContainerUtil.createLockFreeCopyOnWriteList();

  /**
   * Only the EDT writes. {@link #isInConflictQueue} reads from the thread that saves a document.
   * The insertion order is the dialog order.
   */
  private final Set<VirtualFile> myConflictQueue = Collections.synchronizedSet(new LinkedHashSet<>());

  /**
   * The EDT alone touches this.
   */
  private Throwable myConflictAppearedInUnitTest;

  boolean isInConflictQueue(@NotNull VirtualFile file) {
    return myConflictQueue.contains(file);
  }

  void overrideConflictResolution(@NotNull ConflictResolution resolution, @NotNull Disposable parentDisposable) {
    ConflictResolutionOverride wrapper = new ConflictResolutionOverride(resolution);
    ContainerUtil.add(wrapper, myConflictResolutionOverrides, parentDisposable);
  }

  /**
   * How a conflict has to be resolved right now, honouring the precedence documented on {@link #overrideConflictResolution}.
   */
  @NotNull ConflictResolution getConflictResolution() {
    ConflictResolutionOverride override = ContainerUtil.getLastItem(myConflictResolutionOverrides);
    ConflictResolution resolution = override == null ? ConflictResolution.ASK : override.resolution;
    if (resolution != ConflictResolution.MERGE) {
      return resolution;
    }
    if (!isMergeEnabled()) {
      return ConflictResolution.KEEP_MEMORY_CHANGES;
    }
    // MERGE is the only resolution that changes a document without asking, so anyone who opted out of that wins over it
    for (ConflictResolutionOverride other : myConflictResolutionOverrides) {
      if (other.resolution == ConflictResolution.KEEP_MEMORY_CHANGES) {
        return ConflictResolution.KEEP_MEMORY_CHANGES;
      }
    }
    return ConflictResolution.MERGE;
  }

  @RequiresReadLock
  @Nullable ResolvedConflict tryMerge(@NotNull VFileContentChangeEvent event, @Nullable PrefetchedContent prefetched) {
    if (prefetched == null || ConflictResolution.MERGE != getConflictResolution()) {
      return null;
    }
    VirtualFile file = event.getFile();
    Document document = findConflictingDocument(event);
    if (document == null) {
      return null;
    }
    CharSequence baseText = FileDocumentCommonText.getLastKnownCommonText(document);
    if (baseText == null) {
      // resolving the conflict without common base is too fragile
      return null;
    }
    CharSequence diskText = prefetched.decodeText(file);
    CharSequence memoryText = document.getImmutableCharSequence();
    CharSequence mergedText = MergeResolveUtil.tryResolve(diskText, baseText, memoryText, ProgressManager::checkCanceled);
    if (mergedText == null) {
      // the conflict is too complex
      return null;
    }
    return new ResolvedConflict(mergedText, document.getModificationStamp());
  }

  /**
   * This phase cannot tell whether the merge will apply, so it queues every conflict.
   * A merge that applies calls {@link #cancelConflictDialog}.
   */
  void scheduleConflictDialog(@NotNull VFileContentChangeEvent event) {
    ConflictResolution resolution = getConflictResolution();
    if (resolution == ConflictResolution.KEEP_MEMORY_CHANGES) {
      // do nothing, ignoring disk content
      return;
    }
    if (event.isFromSave()) {
      return;
    }
    VirtualFile file = event.getFile();
    if (!file.isValid() || isInConflictQueue(file)) {
      return;
    }
    Document document = findConflictingDocument(event);
    if (document == null) {
      return;
    }
    queueConflictDialog(file, document, event);
  }

  void cancelConflictDialog(@NotNull VirtualFile file) {
    myConflictQueue.remove(file);
  }

  private void queueConflictDialog(@NotNull VirtualFile file, @NotNull Document document, @NotNull VFileContentChangeEvent event) {
    LOG.info("conflict queued for " + file.getName() + "; a merge can still resolve it before the dialog runs");
    LOG.info("  documentStamp:" + document.getModificationStamp());
    LOG.info("  oldFileStamp:" + event.getOldModificationStamp());
    if (ApplicationManager.getApplication().isUnitTestMode()) {
      LOG.info("  fileStamp:" + event.getModificationStamp());
      LOG.info("  document content:" + document.getText());
      // the trace of the last queued conflict, because an earlier one can still leave the queue through a merge
      myConflictAppearedInUnitTest = new Throwable();
    }
    if (myConflictQueue.isEmpty()) {
      ApplicationManager.getApplication().invokeLater(this::showQueuedConflictDialogs);
    }
    myConflictQueue.add(file);
  }

  private void showQueuedConflictDialogs() {
    List<VirtualFile> conflicts;
    synchronized (myConflictQueue) {
      conflicts = new ArrayList<>(myConflictQueue);
      myConflictQueue.clear();
    }
    for (VirtualFile file : conflicts) {
      Document document = FileDocumentManager.getInstance().getCachedDocument(file);
      if (document != null && file.getModificationStamp() != document.getModificationStamp()) {
        LOG.info("reload " + file.getName() + " from disk?");
        if (askReloadFromDisk(file, document)) {
          FileDocumentManager.getInstance().reloadFromDisk(document);
        }
      }
    }
    myConflictAppearedInUnitTest = null;
  }

  @VisibleForTesting
  protected boolean askReloadFromDisk(@NotNull VirtualFile file, @NotNull Document document) {
    if (myConflictAppearedInUnitTest != null) {
      Throwable trace = myConflictAppearedInUnitTest;
      myConflictAppearedInUnitTest = null;
      throw new IllegalStateException(
        "Unexpected memory-disk conflict in tests for " + file.getPath() +
        ", please use FileDocumentManager#reloadFromDisk or avoid VFS refresh",
        trace
      );
    }
    Project project = ProjectLocator.getInstance().guessProjectForFile(file);
    return new ConflictResolverDialog(project).askReloadFromDisk(file, document);
  }

  private static @Nullable Document findConflictingDocument(@NotNull VFileContentChangeEvent event) {
    VirtualFile file = event.getFile();
    Document document = FileDocumentManager.getInstance().getCachedDocument(file);
    if (document == null || !FileDocumentManager.getInstance().isDocumentUnsaved(document)) {
      return null;
    }
    long documentStamp = document.getModificationStamp();
    long oldFileStamp = event.getOldModificationStamp();
    if (documentStamp != oldFileStamp) {
      return document;
    }
    return null;
  }

  /**
   * Deliberately a class and not a record: {@link ContainerUtil#add} unregisters by {@code equals}, so value equality would
   * let one client's disposal drop another client's entry whenever the two asked for the same resolution.
   */
  private static final class ConflictResolutionOverride {
    private final ConflictResolution resolution;

    ConflictResolutionOverride(@NotNull ConflictResolution resolution) {
      this.resolution = resolution;
    }
  }
}
