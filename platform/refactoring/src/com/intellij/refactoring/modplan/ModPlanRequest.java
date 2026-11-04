// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan;

import com.intellij.model.SymbolPointer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.List;
import java.util.function.Supplier;

/**
 * What a caller asks a {@link ModPlanRefactoring} to do: the project, the symbols the refactoring is about, the options and the scope of
 * the search.
 * The symbols are held by pointers, so a request outlives a read action and a dialog.
 *
 * @param subjects     the symbols the refactoring is about; at least one. A question does not change them.
 * @param optionValues the option values of the request. They change after each question: the request of the next step also holds the
 *                     answers, see {@link ModPlanChain#withOptionValues}.
 * @param searchScope  gives the scope where the refactoring searches the usages; call it under the read lock. A question does not
 *                     change it. See {@link #searchScopeSupplier}.
 */
public record ModPlanRequest(@NotNull Project project,
                             @NotNull @Unmodifiable List<SymbolPointer> subjects,
                             @NotNull ModPlanOptionValues optionValues,
                             @NotNull Supplier<@NotNull SearchScope> searchScope) {
  /**
   * @throws IllegalArgumentException if there is no subject
   */
  public ModPlanRequest {
    if (subjects.isEmpty()) {
      throw new IllegalArgumentException("No subject");
    }
    subjects = List.copyOf(subjects);
  }

  /** A request that searches the project. */
  public ModPlanRequest(@NotNull Project project,
                        @NotNull @Unmodifiable List<SymbolPointer> subjects,
                        @NotNull ModPlanOptionValues optionValues) {
    this(project, subjects, optionValues, () -> GlobalSearchScope.projectScope(project));
  }

  /** The supplier of {@code scope} for a request: a local scope is built again from smart pointers on each call. */
  @RequiresReadLock
  public static @NotNull Supplier<@NotNull SearchScope> searchScopeSupplier(@NotNull SearchScope scope) {
    if (!(scope instanceof LocalSearchScope localScope)) return () -> scope;
    List<SmartPsiElementPointer<PsiElement>> pointers = ContainerUtil.map(localScope.getScope(), SmartPointerManager::createPointer);
    String displayName = localScope.getDisplayName();
    boolean ignoreInjectedPsi = localScope.isIgnoreInjectedPsi();
    return () -> new LocalSearchScope(ContainerUtil.mapNotNull(pointers, SmartPsiElementPointer::getElement).toArray(PsiElement.EMPTY_ARRAY),
                                      displayName, ignoreInjectedPsi);
  }
}
