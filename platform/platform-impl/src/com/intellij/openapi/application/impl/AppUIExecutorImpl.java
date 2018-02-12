// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.application.impl;

import com.intellij.ide.IdeEventQueue;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.*;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * @author peter
 */
class AppUIExecutorImpl implements AppUIExecutor {
  private final ModalityState myModality;
  private final Set<Disposable> myDisposables;
  private final ConstrainedExecutor[] myConstraints;

  AppUIExecutorImpl(ModalityState modality) {
    this(modality, Collections.emptySet(), new ConstrainedExecutor[]{new ConstrainedExecutor() {
      @Override
      public boolean isCorrectContext() {
        return ApplicationManager.getApplication().isDispatchThread() && !modality.dominates(ModalityState.current());
      }

      @Override
      public void execute(@NotNull Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(runnable, modality);
      }
    }});
  }

  private AppUIExecutorImpl(ModalityState modality,
                            Set<Disposable> disposables,
                            ConstrainedExecutor[] constraints) {
    myModality = modality;
    myConstraints = constraints;
    myDisposables = disposables;
  }

  @NotNull
  private AppUIExecutor withConstraint(ConstrainedExecutor element) {
    return new AppUIExecutorImpl(myModality, myDisposables, ArrayUtil.append(myConstraints, element));
  }

  @NotNull
  @Override
  public AppUIExecutor later() {
    int eventCount = IdeEventQueue.getInstance().getEventCount();
    return withConstraint(new ConstrainedExecutor() {
      @Override
      public boolean isCorrectContext() {
        return eventCount != IdeEventQueue.getInstance().getEventCount();
      }

      @Override
      public void execute(@NotNull Runnable runnable) {
        ApplicationManager.getApplication().invokeLater(runnable, myModality);
      }
    });
  }

  @NotNull
  @Override
  public AppUIExecutor withDocumentsCommitted(@NotNull Project project) {
    return withConstraint(new ConstrainedExecutor() {
      @Override
      public boolean isCorrectContext() {
        return !PsiDocumentManager.getInstance(project).hasUncommitedDocuments();
      }

      @Override
      public void execute(@NotNull Runnable runnable) {
        PsiDocumentManager.getInstance(project).performLaterWhenAllCommitted(runnable, myModality);
      }
    }).expireWith(project);
  }

  @NotNull
  @Override
  public AppUIExecutor inSmartMode(@NotNull Project project) {
    return withConstraint(new ConstrainedExecutor() {
      @Override
      public boolean isCorrectContext() {
        return !DumbService.getInstance(project).isDumb();
      }

      @Override
      public void execute(@NotNull Runnable runnable) {
        DumbService.getInstance(project).smartInvokeLater(runnable, myModality);
      }
    }).expireWith(project);
  }

  @NotNull
  @Override
  public AppUIExecutor inTransaction(@NotNull Disposable parentDisposable) {
    TransactionId id = TransactionGuard.getInstance().getContextTransaction();
    return withConstraint(new ConstrainedExecutor() {
      @Override
      public boolean isCorrectContext() {
        return TransactionGuard.getInstance().getContextTransaction() != null;
      }

      @Override
      public void execute(@NotNull Runnable runnable) {
        TransactionGuard.getInstance().submitTransaction(parentDisposable, id, runnable);
      }
    }).expireWith(parentDisposable);
  }

  @NotNull
  @Override
  public AppUIExecutor expireWith(@NotNull Disposable parentDisposable) {
    if (myDisposables.contains(parentDisposable)) return this;
    
    Set<Disposable> disposables = ContainerUtil.newHashSet(myDisposables);
    disposables.add(parentDisposable);
    return new AppUIExecutorImpl(myModality, disposables, myConstraints);
  }

  @Override
  public void execute(@NotNull Runnable runnable) {
    CompletableFuture<Void> future = new CompletableFuture<>();

    if (!myDisposables.isEmpty()) {
      List<Disposable> children = new ArrayList<>();
      for (Disposable parent : myDisposables) {
        Disposable child = new Disposable() {
          @Override
          public void dispose() {
            future.cancel(false);
          }
        };
        children.add(child);
        Disposer.register(parent, child);
      }
      future.whenComplete((v, t) -> children.forEach(Disposer::dispose));
    }

    checkConstraints(runnable, future);
  }

  private void checkConstraints(@NotNull Runnable runnable, CompletableFuture<Void> future) {
    Application app = ApplicationManager.getApplication();
    if (!app.isDispatchThread()) {
      app.invokeLater(() -> checkConstraints(runnable, future), myModality);
      return;
    }
    
    if (future.isCancelled()) return;

    for (ConstrainedExecutor constraint : myConstraints) {
      if (!constraint.isCorrectContext()) {
        constraint.execute(() -> checkConstraints(runnable, future));
        return;
      }
    }

    try {
      runnable.run();
      future.complete(null);
    }
    catch (Throwable e) {
      future.completeExceptionally(e);
    }
  }

  private interface ConstrainedExecutor extends Executor {
    boolean isCorrectContext();
  }
}
