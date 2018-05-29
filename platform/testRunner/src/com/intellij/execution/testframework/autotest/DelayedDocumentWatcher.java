// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.testframework.autotest;

import com.intellij.AppTopics;
import com.intellij.codeInsight.lookup.LookupEx;
import com.intellij.codeInsight.lookup.LookupManager;
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
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.PsiErrorElementUtil;
import com.intellij.util.SingleAlarm;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Set;

public class DelayedDocumentWatcher implements AutoTestWatcher {

  // All instance fields should be accessed in EDT
  private final Project myProject;
  private final int myDelayMillis;
  private final Consumer<Integer> myModificationStampConsumer;
  private final Condition<VirtualFile> myChangedFileFilter;
  private final MyDocumentAdapter myListener;

  private Disposable myDisposable;
  private SingleAlarm myAlarm;
  private final Set<VirtualFile> myChangedFiles = new THashSet<>();
  private boolean myDocumentSavingInProgress = false;
  private MessageBusConnection myConnection;
  private int myModificationStamp = 0;

  public DelayedDocumentWatcher(@NotNull Project project,
                                int delayMillis,
                                @NotNull Consumer<Integer> modificationStampConsumer,
                                @Nullable Condition<VirtualFile> changedFileFilter) {
    myProject = project;
    myDelayMillis = delayMillis;
    myModificationStampConsumer = modificationStampConsumer;
    myChangedFileFilter = changedFileFilter;
    myListener = new MyDocumentAdapter();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public void activate() {
    if (myConnection == null) {
      myDisposable = Disposer.newDisposable();
      Disposer.register(myProject, myDisposable);
      EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myListener, myDisposable);
      myConnection = ApplicationManager.getApplication().getMessageBus().connect(myProject);
      myConnection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerListener() {
        @Override
        public void beforeAllDocumentsSaving() {
          myDocumentSavingInProgress = true;
          ApplicationManager.getApplication().invokeLater(() -> myDocumentSavingInProgress = false, ModalityState.any());
        }
      });
      LookupManager.getInstance(myProject).addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          if (LookupManager.PROP_ACTIVE_LOOKUP.equals(evt.getPropertyName()) && evt.getNewValue() == null
              && !myChangedFiles.isEmpty()) {
            myAlarm.cancelAndRequest();
          }
        }
      }, myDisposable);

      myAlarm = new SingleAlarm(new MyRunnable(), myDelayMillis, Alarm.ThreadToUse.SWING_THREAD, myDisposable);
    }
  }

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

  public boolean isUpToDate(int modificationStamp) {
    return myModificationStamp == modificationStamp;
  }

  private class MyDocumentAdapter implements DocumentListener {
    @Override
    public void documentChanged(DocumentEvent event) {
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
        if (myChangedFileFilter != null && !myChangedFileFilter.value(file)) {
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
        if (Disposer.isDisposed(myDisposable)) {
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
        myModificationStampConsumer.consume(myModificationStamp);
      });
    }
  }

  private void asyncCheckErrors(@NotNull Collection<VirtualFile> files,
                                @NotNull Consumer<Boolean> errorsFoundConsumer) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final boolean errorsFound = ReadAction.compute(() -> {
        for (VirtualFile file : files) {
          if (PsiErrorElementUtil.hasErrors(myProject, file)) {
            return true;
          }
        }
        return false;
      });
      ApplicationManager.getApplication().invokeLater(() -> errorsFoundConsumer.consume(errorsFound), ModalityState.any());
    });
  }
}
