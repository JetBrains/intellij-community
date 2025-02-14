// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.autotest;

import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.LookupManagerListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PsiErrorElementUtil;
import com.intellij.util.SingleAlarm;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;

public final class DelayedDocumentWatcher implements AutoTestWatcher {
  // All instance fields should be accessed in EDT
  private final Project myProject;
  private final int myDelayMillis;
  private final IntConsumer myModificationStampConsumer;
  private final Predicate<? super VirtualFile> myChangedFileFilter;
  private final MyDocumentAdapter myListener;
  private final AbstractAutoTestManager myAutoTestManager;

  private Disposable myDisposable;
  private SingleAlarm myAlarm;
  // reduce memory usage
  private final Set<VirtualFile> myChangedFiles = CollectionFactory.createSmallMemoryFootprintSet();
  private boolean myDocumentSavingInProgress = false;
  private MessageBusConnection myConnection;
  private int myModificationStamp = 0;

  public DelayedDocumentWatcher(@NotNull Project project,
                         int delayMillis,
                         @NotNull AbstractAutoTestManager autoTestManager,
                         @Nullable Predicate<? super VirtualFile> changedFileFilter) {
    this(project, delayMillis, null, autoTestManager, changedFileFilter);
  }

  private DelayedDocumentWatcher(@NotNull Project project,
                                 int delayMillis,
                                 @Nullable IntConsumer modificationStampConsumer,
                                 @Nullable AbstractAutoTestManager autoTestManager,
                                 @Nullable Predicate<? super VirtualFile> changedFileFilter) {
    myProject = project;
    myDelayMillis = delayMillis;
    myModificationStampConsumer = modificationStampConsumer;
    myAutoTestManager = autoTestManager;
    myChangedFileFilter = changedFileFilter;
    myListener = new MyDocumentAdapter();
  }

  public @NotNull Project getProject() {
    return myProject;
  }

  @RequiresEdt(generateAssertion = false)
  @Override
  public void activate() {
    if (myConnection == null) {
      myDisposable = Disposer.newDisposable();
      Disposer.register(myProject, myDisposable);
      EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myListener, myDisposable);
      myConnection = ApplicationManager.getApplication().getMessageBus().connect(myProject);
      myConnection.subscribe(FileDocumentManagerListener.TOPIC, new FileDocumentManagerListener() {
        @Override
        public void beforeAllDocumentsSaving() {
          myDocumentSavingInProgress = true;
          ApplicationManager.getApplication().invokeLater(() -> myDocumentSavingInProgress = false, ModalityState.any());
        }
      });
      myConnection.subscribe(LookupManagerListener.TOPIC, new LookupManagerListener() {
        @Override
        public void activeLookupChanged(@Nullable Lookup oldLookup, @Nullable Lookup newLookup) {
          if (newLookup == null && !myChangedFiles.isEmpty()) {
            myAlarm.cancelAndRequest();
          }
        }
      });

      myAlarm = SingleAlarm.Companion.singleEdtAlarm(myDelayMillis, myDisposable, new MyRunnable());
    }
  }

  @RequiresEdt(generateAssertion = false)
  @Override
  public void deactivate() {
    if (myDisposable != null) {
      Disposer.dispose(myDisposable);
      myDisposable = null;
    }
    if (myConnection != null) {
      myConnection.disconnect();
      myConnection = null;
    }
  }

  private class MyDocumentAdapter implements DocumentListener {
    @Override
    public void documentChanged(@NotNull DocumentEvent event) {
      if (myDocumentSavingInProgress) {
        /* When {@link FileDocumentManager#saveAllDocuments} is called,
           {@link com.intellij.openapi.editor.impl.TrailingSpacesStripper} can change a document.
           These needless 'documentChanged' events should be filtered out.
         */
        return;
      }
      final VirtualFile file = FileDocumentManager.getInstance().getFile(event.getDocument());
      if (file == null) {
        return;
      }
      if (!myChangedFiles.contains(file)) {
        if (ProjectUtil.isProjectOrWorkspaceFile(file)) {
          return;
        }
        if (myChangedFileFilter != null && !myChangedFileFilter.test(file)) {
          return;
        }

        myChangedFiles.add(file);
      }

      myAlarm.cancelAndRequest();
      myModificationStamp++;
    }
  }

  private class MyRunnable implements Runnable {
    @Override
    public void run() {
      final int oldModificationStamp = myModificationStamp;
      asyncCheckErrors(myChangedFiles, errorsFound -> {
        if (myDisposable == null) {
          return;
        }
        if (myModificationStamp != oldModificationStamp) {
          // 'documentChanged' event was raised during async checking files for errors
          // Do nothing in that case, this method will be invoked subsequently.
          return;
        }
        LookupEx activeLookup = LookupManager.getInstance(myProject).getActiveLookup();
        if (activeLookup != null && activeLookup.isCompletion()) {
          // This method will be invoked when the completion popup is hidden.
          return;
        }
        if (errorsFound) {
          // Do nothing, if some changed file has syntax errors.
          // This method will be invoked subsequently, when syntax errors are fixed.
          return;
        }
        myChangedFiles.clear();
        if (myModificationStampConsumer != null) {
          myModificationStampConsumer.accept(myModificationStamp);
        }
        else {
          int initialModificationStamp = myModificationStamp;
          Objects.requireNonNull(myAutoTestManager).restartAllAutoTests(() -> {
            return myModificationStamp == initialModificationStamp;
          });
        }
      });
    }
  }

  private void asyncCheckErrors(@NotNull Collection<? extends VirtualFile> files,
                                @NotNull Consumer<? super Boolean> errorsFoundConsumer) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final boolean errorsFound = ReadAction.compute(() -> {
        for (VirtualFile file : files) {
          if (PsiErrorElementUtil.hasErrors(myProject, file)) {
            return true;
          }
        }
        return false;
      });
      ApplicationManager.getApplication().invokeLater(() -> errorsFoundConsumer.accept(errorsFound), ModalityState.any());
    });
  }
}
