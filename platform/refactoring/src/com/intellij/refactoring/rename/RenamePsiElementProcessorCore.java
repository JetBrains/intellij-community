// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

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

  /**
   * An extra string a text occurrence holds, and the string a rename writes instead of it.
   *
   * @param toSearch  the string to look for, beyond the description of the element itself
   * @param toReplace the string to write in its place
   */
  @ApiStatus.Experimental
  record TextOccurrenceSearchStrings(@NotNull String toSearch, @NotNull String toReplace) {
  }

  /**
   * The extra strings of a text occurrence of {@code element}, or null when it has none.
   * <p>
   * A rename reads this only when it searches text occurrences, so a caller which searches none
   * never reaches it.
   */
  @RequiresReadLock
  default @Nullable TextOccurrenceSearchStrings getTextOccurrenceSearchStrings(@NotNull PsiElement element, @NotNull String newName) {
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

  /**
   * The step to run after the write of every element, or null for no step.
   * <p>
   * This method runs on the EDT, inside the write action of the rename, and before the write of
   * {@code element}. So it sees the element as it is under its old name.
   * <p>
   * The {@link Runnable} it returns runs later, on the EDT and inside that same write action, after
   * every element of the rename is written, after the documents are committed, and after the
   * {@link RefactoringElementListener} events fire. So it sees the code whole, and under the new
   * name. It may write PSI. It must not show a dialog, and it must not start background work.
   */
  @RequiresWriteLock
  default @Nullable Runnable getPostRenameCallback(@NotNull PsiElement element,
                                                   @NotNull String newName,
                                                   @NotNull RefactoringElementListener elementListener) {
    return null;
  }

  /**
   * Gets a callback associated with a single renamed element.
   * All callbacks will be run after renaming of all elements is done.
   * <p>
   * The threading of this method and of the callback it returns is the one of
   * {@link #getPostRenameCallback(PsiElement, String, RefactoringElementListener)}.
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
