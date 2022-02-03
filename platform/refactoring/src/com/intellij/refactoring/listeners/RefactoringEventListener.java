// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.listeners;

import com.intellij.util.messages.Topic;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Listener to get high level notifications about performed refactorings in the selected project.
 * 
 * RefactoringEventData depends on the refactoring performed. It should reflect state of the data before/after refactoring.
 */
public interface RefactoringEventListener {
  /**
   * Entry point to attach a client listener. Events are posted on the project bus and its children so connection should be done as following
   * {@code project.getMessageBus().connect(Disposable).subscribe(RefactoringEventListener.REFACTORING_EVENT_TOPIC, new Listener())}
   */
  Topic<RefactoringEventListener> REFACTORING_EVENT_TOPIC = Topic.create("REFACTORING_EVENT_TOPIC", RefactoringEventListener.class);

  /**
   * Is fired when refactoring enters its write phase (find usages, conflict detection phases are passed already) 
   */
  void refactoringStarted(@NotNull String refactoringId, @Nullable RefactoringEventData beforeData);

  /**
   * Is fired when refactoring is completed, probably with conflicts.
   */
  void refactoringDone(@NotNull String refactoringId, @Nullable RefactoringEventData afterData);

  /**
   * Is fired when conflicts are detected. If the next event comes from the same refactoring then conflicts were ignored.
   * @param conflictsData should contain string representation of the conflicts
   */
  void conflictsDetected(@NotNull String refactoringId, @NotNull RefactoringEventData conflictsData);

  /**
   * Is fired when undoable action created on refactoring execution is undone.
   */
  void undoRefactoring(@NotNull String refactoringId);
}
