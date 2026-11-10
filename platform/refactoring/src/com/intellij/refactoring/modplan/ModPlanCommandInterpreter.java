// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan;

import com.intellij.modcommand.ModCommand;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/**
 * Builds one {@link ModCommand} from a {@link ModPlanChain} whose result is the command: the chain runs in the read action of the caller,
 * and each question that is not answered becomes a command of {@link ModPlanQuestion#askCommand}. Its next command goes on with the chain
 * in its own read action. If PSI changed after the questions were asked, the chain runs again from its start with the answers, as
 * {@link ModPlanUiInterpreter} does.
 * <p>
 * Use it everywhere except IDE code on the EDT.
 * <p>
 * The questions start with the values that the storages of the forms remember, if the run loads them; otherwise with the option
 * values of the request, or the defaults of the options. Batch execution and a client without the dialogs take these values. In the
 * unit-test mode, the chain runs headless and nothing is asked, as the legacy refactorings behave in it. Set {@link #ASKS_IN_TESTS} to
 * ask the questions.
 *
 * @see ModPlanUiInterpreter
 */
public final class ModPlanCommandInterpreter {
  /** Set it in a test of the questions themselves: the command asks them in the unit-test mode too. */
  @ApiStatus.Internal
  public static final Key<Boolean> ASKS_IN_TESTS = Key.create("modPlan.command.asks.in.tests");

  private ModPlanCommandInterpreter() {
  }

  /**
   * Runs the chain without asking: every question takes the option values of the request, or the defaults of its options.
   *
   * @param chain the call that gives the chain of {@code request}
   * @return the result of the chain
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  public static <T> @NotNull T interpretHeadless(@NotNull ModPlanRequest request, @NotNull Function<ModPlanChainBuilder, ModPlanChain<T>> chain) {
    ModPlanChain.ChainResult<T> chainResult = ModPlanChain.runToQuestions(request, chain);
    while (true) {
      switch (chainResult) {
        case ModPlanChain.ChainResult.Finished<T> finishedResult -> {
          return finishedResult.value();
        }
        case ModPlanChain.ChainResult.Asked<T> askingResult ->
          chainResult = ModPlanChain.runAfterAnswers(request, chain, askingResult, askingResult.answers());
        default -> throw new IllegalStateException("Unknown chain result: " + chainResult);
      }
    }
  }

  /**
   * Turns the chain into one {@code ModCommand}: the chain runs until a question that the option values do not answer, the question
   * becomes a command of {@link ModPlanQuestion#askCommand}, and its answer goes on with the chain; the last command is the result of the
   * chain.
   *
   * @param title                 the title of the dialogs of the questions
   * @param loadsRememberedValues whether the questions start with the values that the storages of the forms remember. LSP passes false,
   *                              so that a client gets the defaults
   * @param chain                 the call that gives the chain of {@code request}; called again if PSI changed after the questions
   * @return the result of the chain, after the questions; the cancel of a question cancels the command
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  public static @NotNull ModCommand interpret(@NlsContexts.DialogTitle @NotNull String title,
                                   @NotNull ModPlanRequest request,
                                   boolean loadsRememberedValues,
                                   @NotNull Function<ModPlanChainBuilder, ModPlanChain<ModCommand>> chain) {
    if (ApplicationManager.getApplication().isUnitTestMode() && !TestModeFlags.is(ASKS_IN_TESTS)) {
      return interpretHeadless(request, chain);
    }
    return interpretInternal(title, request, loadsRememberedValues, chain, ModPlanChain.runToQuestions(request, chain));
  }

  /** @return the command of the chain from {@code chainResult} on */
  private static @NotNull ModCommand interpretInternal(
    @NlsContexts.DialogTitle @NotNull String title,
    @NotNull ModPlanRequest request,
    boolean loadsRememberedValues,
    @NotNull Function<ModPlanChainBuilder, ModPlanChain<ModCommand>> chain,
    @NotNull ModPlanChain.ChainResult<ModCommand> chainResult
  ) {
    return switch (chainResult) {
      case ModPlanChain.ChainResult.Finished<ModCommand> finishedResult -> finishedResult.value();
      case ModPlanChain.ChainResult.Asked<ModCommand> askingResult -> {
        ModPlanOptionValues initialValues = askingResult.answers();
        ModPlanQuestion.Storage storage = askingResult.question().storage();
        if (loadsRememberedValues && storage != null) {
          initialValues = storage.load(initialValues);
        }
        yield ModPlanQuestion.askCommand(title, askingResult.question(), initialValues, answers -> {
          // The next command of a ModEditOptions runs in a background read action, but executeInBatch runs on the EDT and calls it at once
          // with the default answers, such as in Code Cleanup. There the chain goes on in a background read action under a modal progress.
          ModPlanChain.ChainResult<ModCommand> nextResult = ApplicationManager.getApplication().isDispatchThread()
            ? ProgressManager.getInstance().runProcessWithProgressSynchronously(
                () -> ReadAction.computeBlocking(() -> ModPlanChain.runAfterAnswers(request, chain, askingResult, answers)),
                title, true, request.project())
            : ModPlanChain.runAfterAnswers(request, chain, askingResult, answers);
          return interpretInternal(title, request, loadsRememberedValues, chain, nextResult);
        });
      }
      default -> throw new IllegalStateException("Unknown chain result: " + chainResult);
    };
  }
}
