/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution;

import com.intellij.AppTopics;
import com.intellij.execution.testframework.autotest.AutoTestWatcher;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.PsiErrorElementUtil;
import com.intellij.util.messages.MessageBusConnection;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public class DelayedDocumentWatcher implements AutoTestWatcher {

  // All instance fields are be accessed from EDT
  private final Project myProject;
  private final Alarm myAlarm;
  private final int myDelayMillis;
  private final Consumer<Integer> myModificationStampConsumer;
  private final Condition<VirtualFile> myChangedFileFilter;
  private final MyDocumentAdapter myListener;
  private final Runnable myAlarmRunnable;

  private final Set<VirtualFile> myChangedFiles = new THashSet<>();
  private boolean myDocumentSavingInProgress = false;
  private MessageBusConnection myConnection;
  private int myModificationStamp = 0;
  private Disposable myListenerDisposable;

  public DelayedDocumentWatcher(@NotNull Project project,
                                int delayMillis,
                                @NotNull Consumer<Integer> modificationStampConsumer,
                                @Nullable Condition<VirtualFile> changedFileFilter) {
    myProject = project;
    myAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, myProject);
    myDelayMillis = delayMillis;
    myModificationStampConsumer = modificationStampConsumer;
    myChangedFileFilter = changedFileFilter;
    myListener = new MyDocumentAdapter();
    myAlarmRunnable = new MyRunnable();
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public void activate() {
    if (myConnection == null) {
      myListenerDisposable = Disposer.newDisposable();
      Disposer.register(myProject, myListenerDisposable);
      EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myListener, myListenerDisposable);
      myConnection = ApplicationManager.getApplication().getMessageBus().connect(myProject);
      myConnection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
        @Override
        public void beforeAllDocumentsSaving() {
          myDocumentSavingInProgress = true;
          ApplicationManager.getApplication().invokeLater(() -> myDocumentSavingInProgress = false, ModalityState.any());
        }
      });
    }
  }

  public void deactivate() {
    if (myConnection != null) {
      if (myListenerDisposable != null) {
        Disposer.dispose(myListenerDisposable);
        myListenerDisposable = null;
      }
      myConnection.disconnect();
      myConnection = null;
    }
  }

  public boolean isUpToDate(int modificationStamp) {
    return myModificationStamp == modificationStamp;
  }

  private class MyDocumentAdapter extends DocumentAdapter {
    @Override
    public void documentChanged(DocumentEvent event) {
      if (myDocumentSavingInProgress) {
        /* When {@link FileDocumentManager#saveAllDocuments} is called,
           {@link com.intellij.openapi.editor.impl.TrailingSpacesStripper} can change a document.
           These needless 'documentChanged' events should be filtered out.
         */
        return;
      }
      final Document document = event.getDocument();
      final VirtualFile file = FileDocumentManager.getInstance().getFile(document);
      if (file == null) {
        return;
      }
      if (!myChangedFiles.contains(file)) {
        if (ProjectCoreUtil.isProjectOrWorkspaceFile(file)) {
          return;
        }
        if (myChangedFileFilter != null && !myChangedFileFilter.value(file)) {
          return;
        }

        myChangedFiles.add(file);
      }

      myAlarm.cancelRequest(myAlarmRunnable);
      myAlarm.addRequest(myAlarmRunnable, myDelayMillis);
      myModificationStamp++;
    }
  }

  private class MyRunnable implements Runnable {
    @Override
    public void run() {
      final int oldModificationStamp = myModificationStamp;
      asyncCheckErrors(myChangedFiles, errorsFound -> {
        if (myModificationStamp != oldModificationStamp) {
          // 'documentChanged' event was raised during async checking files for errors
          // Do nothing in that case, this method will be invoked subsequently.
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

  private void asyncCheckErrors(@NotNull final Collection<VirtualFile> files,
                                @NotNull final Consumer<Boolean> errorsFoundConsumer) {
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      final boolean errorsFound = ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
        @Override
        public Boolean compute() {
          for (VirtualFile file : files) {
            if (PsiErrorElementUtil.hasErrors(myProject, file)) {
              return true;
            }
          }
          return false;
        }
      });
      ApplicationManager.getApplication().invokeLater(() -> errorsFoundConsumer.consume(errorsFound), ModalityState.any());
    });
  }
}
