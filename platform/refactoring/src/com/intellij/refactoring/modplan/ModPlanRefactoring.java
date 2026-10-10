// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A refactoring that runs on the ModPlan engine, for every caller: the IDE, MCP, LSP and quick fixes. It shows no UI: its
 * {@linkplain #chain chain} asks its questions where it needs the answers, and the interpreter of a caller answers them, from the user
 * or from the options of the request.
 * <p>
 * A refactoring takes a {@link ModPlanRequest} and reads its own options from it. The safe delete is the example:
 * {@code com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring}. The flow of a run, from the request to the
 * execution, is in {@link com.intellij.refactoring.modplan}.
 */
@ApiStatus.OverrideOnly
public interface ModPlanRefactoring {
  /**
   * @return the id of the refactoring for its events and its help, such as {@code refactoring.safeDelete}
   */
  @NotNull @NonNls String id();

  /**
   * @return the title of the dialogs and of the progress windows of an interactive flow
   */
  @NotNull @NlsContexts.DialogTitle String title();

  /**
   * @return the title of the node of the targets in the preview
   */
  default @NotNull @NlsContexts.Label String targetsNodeText() {
    return RefactoringBundle.message("items.to.be.refactored");
  }

  /**
   * @return the title of the node of the non-code usages in the preview
   */
  default @NotNull @Nls String nonCodeUsagesHeader(int nonCodeUsages, int files) {
    return RefactoringBundle.message("comments.elements.header", UsageViewBundle.getOccurencesString(nonCodeUsages, files));
  }

  /**
   * Checks that every subject is valid and is not in a place that cannot be written, such as a jar; searches nothing. A new
   * refactoring overrides it, calls {@code super}, and adds a check of the language of each subject, such as whether a provider of
   * the language owns it and supports it, see {@code ModPlanSafeDeleteRefactoring}.
   * <p>
   * The read-only status of an ordinary file is not checked here: the interactive flow clears it with
   * {@code ReadonlyStatusHandler.ensureFilesWritable}, and {@link ModPlanExecutor} refuses to write a file that is still read-only.
   *
   * @return true if the refactoring can run for the request
   */
  @RequiresReadLock
  default boolean isSupported(@NotNull ModPlanRequest request) {
    return ContainerUtil.all(request.subjects(), subject -> subject.isValid() && !isInReadOnlyContainer(subject.element()));
  }

  /**
   * @return true if the element is compiled, binary or lies in a file system that cannot be written, such as a jar
   */
  static boolean isInReadOnlyContainer(@Nullable PsiElement element) {
    if (element == null) return false;
    if (element instanceof PsiCompiledElement) return true;
    VirtualFile file = element.getContainingFile() == null ? null : element.getContainingFile().getVirtualFile();
    return file != null && (file.getFileSystem().isReadOnly() || file.getFileType().isBinary());
  }

  /**
   * The {@link ModPlanChain} of one run, which ends with the {@link ModPlanFinalStep} that creates the plan: the caller runs it after the
   * interpreter, after every question. It is the one place of the refactoring that asks questions: the interpreter of a caller answers
   * them, so {@link #plan} and the interactive flow share this code. An interpreter calls it for every run: a run that
   * starts again, because PSI changed or the user wants a fresh preview, calls it again with the answers so far. Nothing is written; run
   * {@link ModPlan.AcceptedPlan#getExecution()} with {@link ModPlanExecutor}.
   *
   * @param chainBuilder holds the request of the run, with the answers so far
   * @return the chain of the run, to the step of the plan; a question that is not answered takes its value from the options of the request
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull ModPlanChain<ModPlanFinalStep<ModPlan>> chain(@NotNull ModPlanChainBuilder chainBuilder);

  /**
   * Finds the usages, the conflicts, and builds the execution. Nothing is asked: the options of the request answer every question.
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  default @NotNull ModPlan plan(@NotNull ModPlanRequest request) {
    return ModPlanCommandInterpreter.interpretHeadless(request, chainBuilder -> chain(chainBuilder).andThen(
      (afterPlan, planStep) -> afterPlan.passValue(planStep.run(afterPlan.getRequest()))));
  }
}
