// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework;

import com.intellij.refactoring.modplan.ModPlanChain;
import com.intellij.refactoring.modplan.ModPlanChainBuilder;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;
import java.util.function.Function;

public final class ModPlanTestUtil {
  private ModPlanTestUtil() {
  }

  /**
   * Runs a {@link ModPlanChain} in the read action of the caller, as
   * {@link com.intellij.refactoring.modplan.ModPlanCommandInterpreter#interpretHeadless} does, but {@code answers} answers each question
   * that is not answered yet, such as a test that records the questions.
   *
   * @param answers gets the question that is not answered yet and the option values so far, and returns them with the answer
   * @return the result of the chain
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  public static <T> @NotNull T runAnswering(
    @NotNull ModPlanRequest request,
    @NotNull Function<ModPlanChainBuilder, ModPlanChain<T>> chain,
    @NotNull BiFunction<? super ModPlanQuestion, ? super ModPlanOptionValues, ModPlanOptionValues> answers
  ) {
    ModPlanChain.ChainResult<T> chainResult = ModPlanChain.runToQuestions(request, chain);
    while (true) {
      switch (chainResult) {
        case ModPlanChain.ChainResult.Finished<T> finishedResult -> {
          return finishedResult.value();
        }
        case ModPlanChain.ChainResult.Asked<T> askingResult -> {
          ModPlanOptionValues answeredValues = answers.apply(askingResult.question(), askingResult.answers());
          chainResult = ModPlanChain.runAfterAnswers(request, chain, askingResult, answeredValues);
        }
        default -> throw new IllegalStateException("Unknown chain result: " + chainResult);
      }
    }
  }
}
