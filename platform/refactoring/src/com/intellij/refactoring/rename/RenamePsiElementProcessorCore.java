// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.listeners.RefactoringElementListener;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.concurrency.annotations.RequiresWriteLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Searches the usages of an element and writes its new name.
 * <p>
 * It holds the part of a rename that asks the user nothing. {@link RenamePsiElementProcessorBase} adds the
 * questions of an element, and {@link RenamePsiElementProcessor} adds the dialog which asks them.
 * {@link HeadlessRenamePsiElementProcessor} adds the answer of every question instead, so the engine of a
 * rename with no user reads that one alone.
 * <p>
 * The callback of {@link #getPostRenameCallback} runs inside a write action, after the write of every
 * element. Every other contract sits on the method itself.
 */
@ApiStatus.Experimental
public interface RenamePsiElementProcessorCore {
  /** Searches nothing beyond the references of an element, and writes the new name of a named element. */
  RenamePsiElementProcessorCore DEFAULT = new RenamePsiElementProcessorCore() {
  };

  /**
   * Writes the new name of {@code element}, and of every usage of it.
   *
   * @param usages the usages the search found, which this method rewrites
   */
  @RequiresWriteLock
  default void renameElement(@NotNull PsiElement element,
                             @NotNull String newName,
                             UsageInfo @NotNull [] usages,
                             @Nullable RefactoringElementListener listener) throws IncorrectOperationException {
    RenameUtil.doRenameGenericNamedElement(element, newName, usages, listener);
  }

  /** The references of {@code element} inside {@code searchScope}. */
  @RequiresReadLock
  default @NotNull @Unmodifiable Collection<PsiReference> findReferences(@NotNull PsiElement element,
                                                                        @NotNull SearchScope searchScope,
                                                                        boolean searchInCommentsAndStrings) {
    return ReferencesSearch.search(element, searchScope).findAll();
  }

  /** The extra string to search in a text occurrence, and the string to write instead of it. */
  @RequiresReadLock
  default @Nullable Pair<String, String> getTextOccurrenceSearchStrings(@NotNull PsiElement element, @NotNull String newName) {
    return null;
  }

  /**
   * The qualified name of {@code element} after the rename, which a non-code usage holds.
   *
   * @param nonJava whether the usage sits in a file of another language
   */
  @RequiresReadLock
  default @Nullable String getQualifiedNameAfterRename(@NotNull PsiElement element, @NotNull String newName, boolean nonJava) {
    return null;
  }

  /** The step to run after the write of every element, or null for no step. */
  @RequiresWriteLock
  default @Nullable Runnable getPostRenameCallback(@NotNull PsiElement element,
                                                   @NotNull String newName,
                                                   @NotNull RefactoringElementListener elementListener) {
    return null;
  }

  /**
   * Gets a callback associated with a single renamed element.
   * All callbacks will be run after renaming of all elements is done.
   *
   * @param element         that was renamed.
   * @param newName         of the {@code element}.
   * @param usages          of the {@code element}.
   * @param allRenames      all elements that were renamed.
   * @param elementListener for sending notifications when some element was refactored.
   * @return callback.
   */
  @RequiresWriteLock
  default @Nullable Runnable getPostRenameCallback(@NotNull PsiElement element,
                                                   @NotNull String newName,
                                                   @NotNull Collection<UsageInfo> usages,
                                                   @NotNull Map<PsiElement, String> allRenames,
                                                   @NotNull RefactoringElementListener elementListener) {
    return getPostRenameCallback(element, newName, elementListener);
  }

  /**
   * Collects the usages that the new name breaks.
   *
   * @param result the usages the search found, which this method adds to
   */
  @RequiresReadLock
  default void findCollisions(@NotNull PsiElement element,
                              @NotNull String newName,
                              @NotNull Map<? extends PsiElement, String> allRenames,
                              @NotNull List<UsageInfo> result) {
  }

  /** The element whose name a string and a comment hold, or null when no text holds it. */
  @RequiresReadLock
  default @Nullable PsiElement getElementToSearchInStringsAndComments(@NotNull PsiElement element) {
    return element;
  }

  /** The usage of {@code element} that {@code ref} makes. */
  @RequiresReadLock
  default @NotNull UsageInfo createUsageInfo(@NotNull PsiElement element,
                                             @NotNull PsiReference ref,
                                             @NotNull PsiElement referenceElement) {
    return RenameUtilBase.createMoveRenameUsageInfo(element, ref, referenceElement);
  }
}
