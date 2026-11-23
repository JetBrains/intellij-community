// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.listeners.RefactoringEventData;
import com.intellij.refactoring.listeners.RefactoringEventListener;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.Collectors;

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
   * Whether an interactive flow shows the usages of the plan in a preview before it writes, as the Preview button of a legacy dialog does.
   * The question that stands for the dialog answers it; a headless caller has no preview.
   */
  ModPlanOption<Boolean> PREVIEW = ModPlanOption.bool("preview", false, RefactoringBundle.messagePointer("preview.button"));

  /**
   * The usages of the plan that the user excluded in the preview, one {@link ModPlan.AcceptedPlan#getUsages() usage} per line, see
   * {@link #isExcluded}. The refactoring leaves them as they are.
   */
  ModPlanOption<String> EXCLUDED_USAGES = ModPlanOption.text("excludedUsages", "", RefactoringBundle.messagePointer("modplan.excluded.usages"));

  /**
   * @return whether the user excluded the usage at {@code location} in the preview, see {@link #EXCLUDED_USAGES}
   */
  static boolean isExcluded(@NotNull ModPlanRequest request, @NotNull SmartPsiFileRange location) {
    String excludedUsages = request.optionValues().get(EXCLUDED_USAGES);
    return !excludedUsages.isEmpty() && excludedUsages.lines().anyMatch(line -> line.equals(lineOf(location)));
  }

  /**
   * @return the value of {@link #EXCLUDED_USAGES} that excludes {@code locations}
   */
  static @NotNull @NonNls String excludedUsagesOf(@NotNull Collection<SmartPsiFileRange> locations) {
    return locations.stream().map(ModPlanRefactoring::lineOf).collect(Collectors.joining("\n"));
  }

  private static @NotNull @NonNls String lineOf(@NotNull SmartPsiFileRange location) {
    VirtualFile file = location.getVirtualFile();
    String path = file == null ? "" : file.getPath();
    Segment range = location.getRange();
    return range == null ? path : path + ":" + range.getStartOffset() + "-" + range.getEndOffset();
  }

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

  /**
   * What the write of a refactoring tells the listeners, as the legacy processor of the refactoring does with {@code getBeforeData},
   * {@code getAfterData} and its refactoring transaction. See {@link ModPlan.AcceptedPlan#getWriteEvents}.
   *
   * @param beforeData     the data of {@link RefactoringEventListener#refactoringStarted}; null if there is none
   * @param elementChanges the elements that the refactoring listeners hear about, see {@link ElementChange}
   * @param afterData      gives the data of {@link RefactoringEventListener#refactoringDone} after the writes; it may give null
   * @param afterWrites    runs in the write command after the writes succeeded, before the listeners: a change outside the files that the
   *                       legacy processor makes after its writes, such as the package prefix of a source root. Only
   *                       {@link ModPlanExecutor} runs it. A caller that applies {@link ModPlanExecution#getCommand} itself does not run it
   */
  record WriteEvent(@Nullable RefactoringEventData beforeData,
                    @NotNull @Unmodifiable List<ElementChange> elementChanges,
                    @NotNull Supplier<@Nullable RefactoringEventData> afterData,
                    @NotNull Runnable afterWrites) {
    /** The events of a refactoring that reports no data and no element changes. */
    public static final @NotNull WriteEvent DEFAULT = new WriteEvent(null, List.of(), () -> null);

    public WriteEvent {
      elementChanges = List.copyOf(elementChanges);
    }

    /** The events of a refactoring that changes nothing but its files. */
    public WriteEvent(@Nullable RefactoringEventData beforeData, @NotNull @Unmodifiable List<ElementChange> elementChanges,
                      @NotNull Supplier<@Nullable RefactoringEventData> afterData) {
      this(beforeData, elementChanges, afterData, () -> { });
    }

    /**
     * @return the events of a refactoring whose data before the write has {@code elements}, as the legacy processors give it by default,
     * with no element changes; it keeps smart pointers to the elements, see {@link ModPlan.AcceptedPlan#getWriteEvents}
     */
    @RequiresReadLock
    public static @NotNull Supplier<@NotNull WriteEvent> ofElements(@NotNull List<? extends PsiElement> elements) {
      List<SmartPsiElementPointer<PsiElement>> pointers = ContainerUtil.map(elements, element -> SmartPointerManager.createPointer(element));
      return () -> {
        RefactoringEventData beforeData = new RefactoringEventData();
        beforeData.addElements(ContainerUtil.mapNotNull(pointers, SmartPsiElementPointer::getElement));
        return new WriteEvent(beforeData, List.of(), () -> null);
      };
    }
  }

  /**
   * An element that keeps its identity at a new name or place, for the refactoring listeners of
   * {@link com.intellij.refactoring.listeners.RefactoringElementListenerProvider}: they get {@code before} before the writes, and the
   * element that {@code after} finds when the writes are done. See {@link WriteEvent}.
   * <p>
   * {@code before} is read only before the writes, as in the legacy refactorings: a moved or renamed element is invalid after them. So
   * {@code after} finds the element by what it took before the writes, such as a smart pointer, a new path or a new name, not through
   * {@code before}. The supplier of the {@link WriteEvent} runs just before the writes, and it gives only a valid {@code before}.
   *
   * @param before the element as it is before the writes
   * @param kind   what happens to the element
   * @param after  finds the element after the writes; null if it cannot be found
   */
  record ElementChange(@NotNull PsiElement before, @NotNull Kind kind, @NotNull Supplier<? extends @Nullable PsiElement> after) {
    public enum Kind {
      /** The element gets a new name, see {@link com.intellij.refactoring.listeners.RefactoringElementListener#elementRenamed}. */
      RENAMED,
      /** The element moves to a new place, see {@link com.intellij.refactoring.listeners.RefactoringElementListener#elementMoved}. */
      MOVED
    }
  }
}
