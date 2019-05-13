// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.availability;

import com.intellij.concurrency.SensitiveProgressWrapper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.util.ProgressIndicatorBase;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.progress.util.ReadTask;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.Semaphore;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PsiAvailabilityServiceImpl extends PsiAvailabilityService {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.availability.PsiAvailabilityServiceImpl");
  private final Project myProject;
  private final PsiDocumentManager myDocumentManager;

  public PsiAvailabilityServiceImpl(Project project) {
    myProject = project;
    myDocumentManager = PsiDocumentManager.getInstance(project);
  }

  @Override
  public boolean makePsiAvailable(@NotNull Document document, @Nls @NotNull String progressTitle) {
    Ref<Boolean> success = Ref.create(false);
    new Task.Modal(myProject, progressTitle, true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        Semaphore semaphore = new Semaphore(1);
        ApplicationManager.getApplication().invokeLater(
          () -> myDocumentManager.performWhenAllCommitted(() -> semaphore.up())
        );
        while (!semaphore.waitFor(50)) {
          indicator.checkCanceled();
        }

        success.set(ReadAction.compute(() -> ensureParsed(document)));
      }
    }.queue();

    return success.get();
  }

  @Override
  public void performWhenPsiAvailable(@NotNull Document document, @NotNull Runnable callback, @Nullable ProgressIndicator indicator) {
    new MyTask(document, callback, indicator).rescheduleIfNeeded();
  }

  private boolean ensureParsed(@NotNull Document document) {
    LOG.trace("ensureParsed");
    if (myDocumentManager.isCommitted(document)) {
      final PsiFile baseFile = myDocumentManager.getPsiFile(document);
      if (baseFile != null) {
        for (PsiFile file : baseFile.getViewProvider().getAllFiles()) {
          ProgressManager.checkCanceled();
          file.getNode().getFirstChildNode();
        }
        return true;
      }
    }

    return false;
  }


  private class MyTask {
    private final Document myDocument;
    private final long myOriginalStamp;
    private final Runnable myAction;
    private final ProgressIndicator myOriginalIndicator;

    private MyTask(Document document, Runnable action, ProgressIndicator indicator) {
      myDocument = document;
      myOriginalStamp = document.getModificationStamp();
      myAction = action;
      myOriginalIndicator = indicator == null ? new ProgressIndicatorBase() : indicator;
    }

    // If the document wasn't changed, but we're canceled by some write action, we should restart and try again
    //If the document is changed, we shouldn't restart, the caller will call us again independently.
    private boolean isObsolete(ProgressIndicator indicator) {
      return myProject.isDisposed() || indicator.isCanceled() || myDocument.getModificationStamp() != myOriginalStamp;
    }

    void rescheduleIfNeeded() {
      LOG.trace("rescheduleIfNeeded");
      switch (getParsedState(myOriginalIndicator)) {
        case Obsolete:
          return;
        case Parsed:
          myAction.run();
          return;
        case Uncommitted:
        case NotParsed:
          break;
      }

      // So we can bail out early:
      //  - If everything is OK already, call back right away (without invokeLater)
      //  - If after the commit the FileAstNode is parsed, invokeLater and call back (possibly retrying, if something has changed)
      //  - And only if after the commit (which could be a no-op) the FileAstNode is not parsed, spawn a thread for ensureParsed()
      //
      // So, for sane languages, usually no extra background thread is spawned after the commit,
      // which eventually is an ultimate goal of doing all of this.
      myDocumentManager.performForCommittedDocument(myDocument, () -> ApplicationManager.getApplication().invokeLater(() -> {
        switch (getParsedState(myOriginalIndicator)) {
          case Obsolete:
            return;
          case Uncommitted:
            rescheduleIfNeeded();
            return;
          case NotParsed:
            break;
          case Parsed:
            myAction.run();
            return;
        }

        LOG.trace("scheduling background task");
        ProgressIndicatorUtils.scheduleWithWriteActionPriority(new SensitiveProgressWrapper(myOriginalIndicator), new ReadTask() {
          @Nullable
          @Override
          public Continuation performInReadAction(@NotNull ProgressIndicator indicator) throws ProcessCanceledException {
            if (isObsolete(indicator)) {
              return null;
            }

            if (ensureParsed(myDocument)) {
              return new Continuation(() -> {
                switch (getParsedState(indicator)) {
                  case Obsolete:
                    break;
                  case Uncommitted:
                  case NotParsed:
                    rescheduleIfNeeded();
                    break;
                  case Parsed:
                    myAction.run();
                    break;
                }
              });
            }

            return null;
          }

          @Override
          public void onCanceled(@NotNull ProgressIndicator indicator) {
            rescheduleIfNeeded();
          }
        });
      }));

    }

    ParsedState getParsedState(@NotNull ProgressIndicator indicator) {
      if (isObsolete(indicator)) {
        return ParsedState.Obsolete;
      }

      final PsiFile baseFile = myDocumentManager.getPsiFile(myDocument);
      if (baseFile == null) {
        return ParsedState.Obsolete;
      }

      if (myDocumentManager.isUncommited(myDocument)) {
        return ParsedState.Uncommitted;
      }

      for (PsiFile file : baseFile.getViewProvider().getAllFiles()) {
        if (file.getNode() != null && !file.getNode().isParsed()) {
          return ParsedState.NotParsed;
        }
      }

      return ParsedState.Parsed;
    }
  }

  enum ParsedState {
    Obsolete,
    Uncommitted,
    NotParsed,
    Parsed
  }
}
