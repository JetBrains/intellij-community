// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl;

import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.impl.cmd.CmdEvent;
import com.intellij.openapi.command.impl.cmd.CmdIdService;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.command.undo.UndoableAction;
import com.intellij.openapi.components.ComponentManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.NlsActions.ActionDescription;
import com.intellij.openapi.util.NlsActions.ActionText;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ExternalChangeActionUtil;
import com.intellij.serviceContainer.NonInjectable;
import com.intellij.util.ObjectUtils;
import com.intellij.util.concurrency.ThreadingAssertions;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.TestOnly;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class UndoManagerImpl extends UndoManager implements Disposable {
  private static final Logger LOG = Logger.getInstance(UndoManagerImpl.class);

  @SuppressWarnings("StaticNonFinalField")
  @TestOnly
  public static boolean ourNeverAskUser = false;

  public static boolean isRefresh() {
    return ExternalChangeActionUtil.isExternalChangeInProgress();
  }

  public static int getGlobalUndoLimit() {
    return Registry.intValue("undo.globalUndoLimit");
  }

  public static int getDocumentUndoLimit() {
    return Registry.intValue("undo.documentUndoLimit");
  }

  private final @Nullable Project project;
  private final @NotNull UndoClientState state;
  private @Nullable CurrentEditorProvider overriddenEditorProvider;

  @SuppressWarnings("unused")
  private UndoManagerImpl(@NotNull Project project) {
    this(project, UndoCapabilities.Default.INSTANCE);
  }

  @SuppressWarnings("unused")
  private UndoManagerImpl() {
    this(null, UndoCapabilities.Default.INSTANCE);
  }

  @ApiStatus.Internal
  @NonInjectable
  protected UndoManagerImpl(@Nullable ComponentManager componentManager, @NotNull UndoCapabilities undoCapabilities) {
    this.project = (componentManager instanceof Project project0) ? project0 : null;
    this.state = new UndoClientState(this.project, undoCapabilities);
  }

  @Override
  public boolean isUndoAvailable(@Nullable FileEditor editor) {
    return isUndoRedoAvailable(editor, true);
  }

  @Override
  public boolean isRedoAvailable(@Nullable FileEditor editor) {
    return isUndoRedoAvailable(editor, false);
  }

  @Override
  public @NotNull Pair<String, String> getUndoActionNameAndDescription(FileEditor editor) {
    return getUndoOrRedoActionNameAndDescription(editor, true);
  }

  @Override
  public @NotNull Pair<String, String> getRedoActionNameAndDescription(FileEditor editor) {
    return getUndoOrRedoActionNameAndDescription(editor, false);
  }

  @Override
  public void undo(@Nullable FileEditor editor) {
    ThreadingAssertions.assertEventDispatchThread();
    LOG.assertTrue(isUndoAvailable(editor));
    undoOrRedo(editor, true);
  }

  @Override
  public void redo(@Nullable FileEditor editor) {
    ThreadingAssertions.assertEventDispatchThread();
    LOG.assertTrue(isRedoAvailable(editor));
    undoOrRedo(editor, false);
  }

  @Override
  public boolean isUndoInProgress() {
    return state.isUndoInProgress();
  }

  @Override
  public boolean isRedoInProgress() {
    return state.isRedoInProgress();
  }

  @Override
  public void nonundoableActionPerformed(@NotNull DocumentReference ref, boolean isGlobal) {
    ThreadingAssertions.assertEventDispatchThread();
    if (project != null && project.isDisposed()) {
      return;
    }
    undoableActionPerformed(new NonUndoableAction(ref, isGlobal));
  }

  @Override
  public void undoableActionPerformed(@NotNull UndoableAction action) {
    ThreadingAssertions.assertEventDispatchThread();
    if (project != null && project.isDisposed()) {
      return;
    }
    state.addUndoableAction(getEditorProvider(), action);
  }

  @Override
  public long getNextUndoNanoTime(@NotNull FileEditor editor) {
    return state.getNextNanoTime(editor, true);
  }

  @Override
  public long getNextRedoNanoTime(@NotNull FileEditor editor) {
    return state.getNextNanoTime(editor, false);
  }

  @Override
  public boolean isNextUndoAskConfirmation(@NotNull FileEditor editor) {
    return state.isNextAskConfirmation(editor, true);
  }

  @Override
  public boolean isNextRedoAskConfirmation(@NotNull FileEditor editor) {
    return state.isNextAskConfirmation(editor, false);
  }

  public boolean isActive() {
    return state.isActiveForCurrentProject();
  }

  public void addDocumentAsAffected(@NotNull Document document) {
    state.addDocumentAsAffected(DocumentReferenceManager.getInstance().create(document));
  }

  public void markCurrentCommandAsGlobal() {
    state.markCurrentCommandAsGlobal();
  }

  public void addAffectedFiles(VirtualFile @NotNull ... files) {
    state.addAffectedFiles(files);
  }

  public void invalidateActionsFor(@NotNull DocumentReference ref) {
    ThreadingAssertions.assertEventDispatchThread();
    state.invalidateActions(ref);
  }

  public @NotNull CurrentEditorProvider getEditorProvider() {
    CurrentEditorProvider overriddenProvider = overriddenEditorProvider;
    CurrentEditorProvider editorProvider = overriddenProvider != null
        ? overriddenProvider
        : ProgressManager.getInstance().computeInNonCancelableSection(CurrentEditorProvider::getInstance);
    return new StableEditorProvider(editorProvider);
  }

  public @Nullable Project getProject() {
    return project;
  }

  @ApiStatus.Internal
  public @Nullable ResetUndoHistoryToken createResetUndoHistoryToken(@NotNull FileEditor editor) {
    Collection<DocumentReference> references = UndoDocumentUtil.getDocumentReferences(editor);
    if (references.size() != 1) {
      return null;
    }
    DocumentReference reference = references.iterator().next();
    LocalUndoRedoSnapshot snapshot = getUndoRedoSnapshotForDocument(reference);
    if (snapshot == null) {
      return null;
    }
    return new ResetUndoHistoryToken(this, reference, snapshot);
  }

  @ApiStatus.Internal
  public @NotNull String dumpState(@Nullable FileEditor editor, @NotNull String title) {
    String editorString = "dump for " + (editor == null ? "GLOBAL" : editor.toString());
    String undoAvailable = String.valueOf(isUndoAvailable(editor)).toUpperCase(Locale.ROOT);
    String redoAvailable = String.valueOf(isRedoAvailable(editor)).toUpperCase(Locale.ROOT);
    Pair<String, String> undoDescription = getUndoActionNameAndDescription(editor);
    Pair<String, String> redoDescription = getRedoActionNameAndDescription(editor);
    String undoStatus = "undo: %s, %s, %s".formatted(undoAvailable, undoDescription.getFirst(), undoDescription.getSecond());
    String redoStatus = "redo: %s, %s, %s".formatted(redoAvailable, redoDescription.getFirst(), redoDescription.getSecond());
    String commandHistory = CmdIdService.getInstance().historyDump();
    String stacks = state.dump(editor);
    return """

      _____________________________________________________________________________________________________________________
      %s
      %s
      %s
      %s
      %s
      %s
      _____________________________________________________________________________________________________________________
      """.formatted(title, editorString, undoStatus, redoStatus, commandHistory, stacks);
  }

  @ApiStatus.Internal
  public void clearDocumentReferences(@NotNull Document document) {
    ThreadingAssertions.assertEventDispatchThread();
    state.clearDocumentReferences(document);
  }

  @ApiStatus.Internal
  public void clearStacks(@Nullable FileEditor editor) {
    state.clearStacks(editor);
  }

  @ApiStatus.Internal
  public final int getStackSize(@Nullable DocumentReference docRef, boolean isUndo) {
    return state.getStackSize(docRef, isUndo);
  }

  @ApiStatus.Internal
  @Override
  public void dispose() {
    state.clearStacks();
  }

  @ApiStatus.Internal
  public @NotNull UndoCapabilities getUndoCapabilities() {
    return state.getUndoCapabilities();
  }

  @ApiStatus.Internal
  protected void undoOrRedo(@Nullable FileEditor editor, boolean isUndo) {
    String commandName = getUndoOrRedoActionNameAndDescription(editor, isUndo).getSecond();
    Disposable disposable = Disposer.newDisposable();
    Runnable beforeUndoRedoStarted = () -> notifyUndoRedoStarted(editor, disposable, isUndo);
    try {
      state.undoOrRedo(editor, commandName, beforeUndoRedoStarted, isUndo);
    } finally {
      Disposer.dispose(disposable);
    }
  }

  void onCommandStarted(@NotNull CmdEvent cmdStartEvent) {
    for (UndoProvider undoProvider : getUndoProviders()) {
      undoProvider.commandStarted(cmdStartEvent.project());
    }
    state.commandStarted(cmdStartEvent, getEditorProvider());
  }

  void onCommandFinished(@NotNull CmdEvent cmdFinishEvent) {
    state.commandFinished(cmdFinishEvent);
    for (UndoProvider undoProvider : getUndoProviders()) {
      undoProvider.commandFinished(cmdFinishEvent.project());
    }
  }

  void onCommandFakeFinished(@NotNull CmdEvent cmdFakeFinishEvent) {
    state.commandFakeFinished(cmdFakeFinishEvent);
  }

  void addAffectedDocuments(Document @NotNull ... docs) {
    state.addAffectedDocuments(docs);
  }

  @Nullable LocalUndoRedoSnapshot getUndoRedoSnapshotForDocument(@NotNull DocumentReference reference) {
    return state.getUndoRedoSnapshotForDocument(reference);
  }

  boolean resetLocalHistory(@NotNull DocumentReference reference, @NotNull LocalUndoRedoSnapshot snapshot) {
    return state.resetLocalHistory(reference, snapshot);
  }

  boolean isUndoRedoAvailable(@NotNull DocumentReference docRef, boolean undo) {
    return state.isUndoRedoAvailable(Collections.singleton(docRef), undo);
  }

  @TestOnly
  public void setOverriddenEditorProvider(@Nullable CurrentEditorProvider p) {
    overriddenEditorProvider = p;
  }

  @TestOnly
  public void dropHistoryInTests() {
    flushMergers();
    state.dropHistoryInTests();
  }

  @TestOnly
  public void flushCurrentCommandMerger() {
    state.flushCommandMergerInTests(CommandMergerFlushReason.MANAGER_FORCE);
  }

  @TestOnly
  public void clearUndoRedoQueueInTests(@NotNull VirtualFile file) {
    DocumentReference docRef = DocumentReferenceManager.getInstance().create(file);
    state.clearUndoRedoQueue(docRef);
  }

  @TestOnly
  public void clearUndoRedoQueueInTests(@NotNull Document document) {
    DocumentReference docRef = DocumentReferenceManager.getInstance().create(document);
    state.clearUndoRedoQueue(docRef);
  }

  @ApiStatus.Internal
  @TestOnly
  public boolean isInsideCommand() {
    return state.isInsideCommand();
  }

  private void notifyUndoRedoStarted(@Nullable FileEditor editor, @NotNull Disposable disposable, boolean isUndo) {
    ApplicationManager.getApplication()
      .getMessageBus()
      .syncPublisher(UndoRedoListener.Companion.getTOPIC())
      .undoRedoStarted(project, this, editor, isUndo, disposable);
  }

  private @NotNull Pair<@ActionText String, @ActionDescription String> getUndoOrRedoActionNameAndDescription(@Nullable FileEditor editor, boolean undo) {
    String desc = null;
    if (state.isUndoRedoAvailable(editor, undo)) {
      desc = state.getLastCommandName(editor, undo);
    }
    if (desc == null) {
      desc = "";
    }
    String shortActionName = StringUtil.first(desc, 30, true);
    if (desc.isEmpty()) {
      desc = undo ? ActionsBundle.message("action.undo.description.empty")
                  : ActionsBundle.message("action.redo.description.empty");
    }
    String name = undo ? ActionsBundle.message("action.undo.text", shortActionName)
                       : ActionsBundle.message("action.redo.text", shortActionName);
    String description = undo ? ActionsBundle.message("action.undo.description", desc)
                              : ActionsBundle.message("action.redo.description", desc);
    return Pair.create(name.trim(), description.trim());
  }

  private boolean isUndoRedoAvailable(@Nullable FileEditor editor, boolean undo) {
    ThreadingAssertions.assertEventDispatchThread();
    return state.isUndoRedoAvailable(editor, undo);
  }

  private @NotNull List<UndoProvider> getUndoProviders() {
    return ProgressManager.getInstance().computeInNonCancelableSection(
      () -> project == null
            ? UndoProvider.EP_NAME.getExtensionList()
            : UndoProvider.PROJECT_EP_NAME.getExtensionList(project)
    );
  }

  @TestOnly
  private void flushMergers() {
    assert project == null || !project.isDisposed() : project;
    // Run dummy command in order to flush all mergers...
    //noinspection HardCodedStringLiteral
    CommandProcessor.getInstance().executeCommand(project, EmptyRunnable.getInstance(), "Dummy", null);
  }

  @Override
  public String toString() {
    return "UndoManager for " + ObjectUtils.notNull(project, "application");
  }
}
