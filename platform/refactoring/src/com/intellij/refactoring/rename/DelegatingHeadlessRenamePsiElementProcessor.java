// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.psi.PsiElement;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

/**
 * Renames with no user through the methods a rename with a user runs.
 * <p>
 * Implement this on a {@link RenamePsiElementProcessorBase} that asks the user nothing. Every method below
 * then answers with the same code as an interactive rename, and the processor writes no method of its own.
 * A headless rename also searches the usages and writes the new name with the processor, because
 * {@link #processorCore()} hands the processor out. So the registration on
 * {@link HeadlessRenamePsiElementProcessor} carries the whole rename.
 * <p>
 * A processor that does ask overrides the one method which holds its question. Do not repeat the body of the
 * interactive method there. Give that method a parameter for the question, and call it from the override with
 * the default answer.
 */
@ApiStatus.Experimental
public interface DelegatingHeadlessRenamePsiElementProcessor extends HeadlessRenamePsiElementProcessor {
  @Override
  default @NotNull RenamePsiElementProcessorCore processorCore() {
    return self();
  }

  @Override
  @RequiresReadLock
  default boolean canProcessElementHeadless(@NotNull PsiElement element) {
    return self().canProcessElement(element);
  }

  @Override
  @RequiresReadLock
  default @Nullable PsiElement substituteElementToRenameHeadless(@NotNull PsiElement element) {
    return self().substituteElementToRename(element, null);
  }

  @Override
  @RequiresReadLock
  default void prepareRenamingHeadless(@NotNull PsiElement element,
                                       @NotNull String newName,
                                       @NotNull Map<PsiElement, String> allRenames) {
    self().prepareRenaming(element, newName, allRenames);
  }

  @Override
  @RequiresReadLock
  default void findExistingNameConflictsHeadless(@NotNull PsiElement element,
                                                 @NotNull String newName,
                                                 @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                                                 @NotNull Map<PsiElement, String> allRenames) {
    self().findExistingNameConflicts(element, newName, conflicts, allRenames);
  }

  /**
   * This, as the processor it must be.
   * <p>
   * Every method above delegates to the interactive method of the processor, so an implementation of this
   * interface has to be one. An implementation that answers from scratch implements
   * {@link HeadlessRenamePsiElementProcessor} instead, and that one needs no processor.
   */
  private RenamePsiElementProcessorBase self() {
    return (RenamePsiElementProcessorBase)this;
  }
}
