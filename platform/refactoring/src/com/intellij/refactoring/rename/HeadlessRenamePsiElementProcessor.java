// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

/**
 * Renames for a caller that has no user, such as an MCP tool or a language server.
 * <p>
 * This extension point stands on its own, and a headless rename reads only this one. It takes the processors
 * of an element as an interactive rename does: every one of them collects the other elements to rename, and
 * the first one that claims the element answers the other two questions. The first one also searches the
 * usages and writes the new name, with the {@link #processorCore()} it hands out. So a registration here is
 * enough on its own, and a product with no interactive rename registers nothing else.
 * <p>
 * A product with an interactive rename needs the {@code renamePsiElementProcessor} registration too, because
 * a rename with a user reads that point alone. Such a registration carries the same {@code order} and
 * {@code id} as the one here, and it sits next to it.
 * <p>
 * A processor that asks the user nothing implements {@link DelegatingHeadlessRenamePsiElementProcessor}
 * instead, and writes no method at all. A processor with subclasses implements neither, and registers a
 * dedicated subclass of itself here. A subclass must not inherit the statement that it renames with no user.
 * <p>
 * Every method here runs on a background thread.
 */
@ApiStatus.Experimental
public interface HeadlessRenamePsiElementProcessor {
  ExtensionPointName<HeadlessRenamePsiElementProcessor> EP_NAME =
    ExtensionPointName.create("com.intellij.headlessRenamePsiElementProcessor");

  /**
   * The processor of {@code element} on this extension point, as the processor it is.
   * <p>
   * A processor which dispatches a question per element reads this, instead of
   * {@code RenamePsiElementProcessor.forElement}. That one reads the interactive extension point, which a
   * product with no interactive rename leaves empty, and the question then goes to the default processor.
   *
   * @return the first processor which claims {@code element} and is a {@link RenamePsiElementProcessorBase},
   * or {@link RenamePsiElementProcessorBase#DEFAULT} when none does
   */
  static @NotNull RenamePsiElementProcessorBase processorOf(@NotNull PsiElement element) {
    for (HeadlessRenamePsiElementProcessor processor : EP_NAME.getExtensionList()) {
      if (processor.canProcessElementHeadless(element) && processor instanceof RenamePsiElementProcessorBase base) {
        return base;
      }
    }
    return RenamePsiElementProcessorBase.DEFAULT;
  }

  /**
   * The part of this processor which searches the usages of an element and writes its new name.
   * <p>
   * This interface does not extend {@link RenamePsiElementProcessorCore}, and it hands the core out
   * instead. A processor of a language reaches the eight methods of the core through its class as well,
   * and two paths to one method make a Kotlin {@code super} call ambiguous.
   */
  @NotNull RenamePsiElementProcessorCore processorCore();

  /**
   * Whether this renames {@code element}.
   * <p>
   * It answers what {@code canProcessElement} of the processor answers.
   */
  @RequiresReadLock
  boolean canProcessElementHeadless(@NotNull PsiElement element);

  /**
   * The element to rename instead of {@code element}, with every question answered by a default.
   * <p>
   * Answer so that the code stays consistent. Rename the base method, and rename both accessors of a
   * property. Return null to refuse the rename.
   * <p>
   * It answers with no user what {@code substituteElementToRename} of the processor answers with one.
   */
  @RequiresReadLock
  @Nullable PsiElement substituteElementToRenameHeadless(@NotNull PsiElement element);

  /**
   * Collects the other elements the rename must change, with every question answered by a default.
   * <p>
   * It answers with no user what {@code prepareRenaming} of the processor answers with one. It takes
   * the same three parameters, because {@link RenameProcessor} calls that method with three. A
   * processor which overrides the variant with a search scope is reached through the three-parameter
   * one, and a processor which overrides the three-parameter one is not reached through the other.
   *
   * @param allRenames the map from an element to its new name, which this method adds to
   */
  @RequiresReadLock
  void prepareRenamingHeadless(@NotNull PsiElement element,
                               @NotNull String newName,
                               @NotNull Map<PsiElement, String> allRenames);

  /**
   * Collects the conflicts of the rename of {@code element} to {@code newName}.
   * <p>
   * It answers what {@code findExistingNameConflicts} of the processor answers. That method asks the user
   * nothing, so there is no question here to answer by a default. The engine still reads it here, because
   * the interactive extension point can answer with another processor.
   *
   * @param conflicts  the map from an element to a conflict message, which this method adds to
   * @param allRenames the other elements of the rename, which this method must not change
   */
  @RequiresReadLock
  void findExistingNameConflictsHeadless(@NotNull PsiElement element,
                                         @NotNull String newName,
                                         @NotNull MultiMap<PsiElement, @DialogMessage String> conflicts,
                                         @NotNull Map<PsiElement, String> allRenames);
}
