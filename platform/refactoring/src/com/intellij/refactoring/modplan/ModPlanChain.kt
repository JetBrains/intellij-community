// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan

import com.intellij.model.SymbolPointer
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.concurrency.annotations.RequiresReadLock
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract
import java.util.function.Function

/**
 * The questions of one call of a refactoring and its result. The code cannot wait for an answer in its read action, so the code after a
 * question is a function that an interpreter calls when the answer is there: [ModPlanUiInterpreter] asks in dialogs,
 * [ModPlanCommandInterpreter] turns the questions into commands or answers them with the option values. The chain is immutable.
 *
 * A call gets a [ModPlanChainBuilder] and returns one of:
 * - [ModPlanChainBuilder.passValue]: the result is known;
 * - [ModPlanChainBuilder.ask]: the result needs an answer, the function after it gets a builder whose request holds the answer;
 * - [andThen]: the result needs the value of a helper that can ask, such as the elements to search;
 * - [ModPlanChainBuilder.finish]: the search and the edits, which the caller of the whole chain runs after every question.
 *
 * ```
 * return chainBuilder.ask(hierarchyQuestion, answered ->
 *   answered.passValue(answered.getRequest().optionValues().get(DELETE_HIERARCHY) ? hierarchy : List.of(elementSubject)));
 * ```
 * **No live PSI after a question.** A function after a question runs in another read action, maybe much later, and maybe more than once:
 * if PSI changed, the interpreter runs the chain again from its start with the answers so far. So it captures only
 * [SymbolPointer]s, smart pointers and values, and changes nothing that it captures.
 *
 * Where the chain is in the whole flow: [com.intellij.refactoring.modplan].
 *
 * @param T the result of the call
 */
sealed class ModPlanChain<T>(private val request: ModPlanRequest) {
  /**
   * Goes on with the value that this chain ends with, such as the elements that a helper gives.
   *
   * [afterValue] gets the value in the read action of the `passValue` that gives it, so the value can hold live PSI elements. The
   * function after a question of this chain still captures none.
   *
   * @param afterValue goes on with the builder after this chain and its value
   * @return the chain that runs this chain, then the chain of [afterValue]
   */
  @Contract(pure = true)
  fun <W> andThen(afterValue: AfterValue<W, in T>): ModPlanChain<W> = continued(request, this, afterValue)

  /**
   * The code after a value of [ModPlanChain.andThen]: it goes on with the builder and the value.
   *
   * @param T the result of the call
   * @param V the value
   */
  fun interface AfterValue<T, V> {
    /**
     * @param chainBuilder the builder of the call after the value, with the answers so far in [ModPlanChainBuilder.request]
     * @param value the value of the helper
     * @return the chain that goes on from there, often with [ModPlanChainBuilder.passValue]
     */
    fun goOn(chainBuilder: ModPlanChainBuilder, value: V): ModPlanChain<T>
  }

  /** Where an interpreter stopped in a chain: at its end, or at a question that a user or a command answers. */
  sealed interface ChainResult<T> {
    /**
     * The chain has nothing more to do.
     *
     * @property answers the option values at the end of the run, with its answers; a rerun of the refactoring starts from them
     */
    @JvmRecord
    data class Finished<T>(val value: T, val answers: ModPlanOptionValues) : ChainResult<T>

    /**
     * The chain asks.
     *
     * @property question the question that [answers] do not answer
     * @property answers the option values at the question
     * @property resume goes on with the option values that answer the question
     * @property modificationCount the PSI modification count when the chain stopped at the question, see [runAfterAnswers]
     */
    @ApiStatus.Internal
    @JvmRecord
    data class Asked<T>(
      val question: ModPlanQuestion,
      val answers: ModPlanOptionValues,
      val resume: Function<ModPlanOptionValues, out ModPlanChain<T>>,
      val modificationCount: Long,
    ) : ChainResult<T>
  }

  companion object {
    /** @return a request for the subjects of [request] with [optionValues] */
    @JvmStatic
    fun withOptionValues(request: ModPlanRequest, optionValues: ModPlanOptionValues): ModPlanRequest =
      ModPlanRequest(request.project(), request.subjects(), optionValues)

    /**
     * Runs [chain] for [request] up to the next question that its option values do not answer. An interpreter calls it at the start
     * of a run. It calls it on a background thread, or on the EDT under a write action, see [ModPlanUiInterpreter].
     *
     * @param chain the call that gives the chain of [request]
     */
    @ApiStatus.Internal
    @RequiresReadLock
    @JvmStatic
    fun <T> runToQuestions(request: ModPlanRequest, chain: Function<ModPlanChainBuilder, ModPlanChain<T>>): ChainResult<T> =
      runFrom(chain.apply(BuilderImpl(request)), request.optionValues(), request.project())

    /**
     * Runs the chain after the answer to [askingResult]: on from the question if PSI did not change since it, else from the start
     * for [request] with the answers. An interpreter calls it on a background thread, or on the EDT under a write action, see
     * [ModPlanUiInterpreter].
     *
     * @param request the request of the caller
     * @param chain the call, as for [runToQuestions]
     * @param answers the option values of [ChainResult.Asked.answers] with the answer to the question
     */
    @ApiStatus.Internal
    @RequiresReadLock
    @JvmStatic
    fun <T> runAfterAnswers(
      request: ModPlanRequest,
      chain: Function<ModPlanChainBuilder, ModPlanChain<T>>,
      askingResult: ChainResult.Asked<T>,
      answers: ModPlanOptionValues,
    ): ChainResult<T> {
      val project = request.project()
      if (PsiModificationTracker.getInstance(project).modificationCount == askingResult.modificationCount) {
        return runFrom(askingResult.resume.apply(answers), answers, project)
      }
      return runToQuestions(withOptionValues(request, answers), chain)
    }

    /** @return [innerChain], then the chain of [afterValue], with [request] and the answers of the interpreter */
    private fun <V, W> continued(request: ModPlanRequest, innerChain: ModPlanChain<V>, afterValue: AfterValue<W, in V>): ModPlanChain<W> {
      return when (innerChain) {
        // No question goes on in the loop of the interpreter, so the stack does not grow with every inner chain. The code after the value
        // gets the request of this call with the answers of the interpreter: the inner chain may have asked, and its request can be
        // another one.
        is ValueChain -> AskingChain(request, null) { answers -> afterValue.goOn(builderOf(request, answers), innerChain.result) }
        // This only composes: the resume runs later, when the interpreter has the answer.
        is AskingChain -> AskingChain(request, innerChain.question) { answers ->
          continued(request, innerChain.resume.apply(answers), afterValue)
        }
      }
    }

    /** Goes along [chain] while its questions are answered: a loop, so a long chain does not grow the stack. */
    private fun <T> runFrom(chain: ModPlanChain<T>, answers: ModPlanOptionValues, project: Project): ChainResult<T> {
      var currentChain = chain
      while (true) {
        when (currentChain) {
          is ValueChain -> return ChainResult.Finished(currentChain.result, answers)
          is AskingChain -> {
            val question = currentChain.question
            if (question != null && !question.isAnswered(answers)) {
              val modificationCount = PsiModificationTracker.getInstance(project).modificationCount
              return ChainResult.Asked(question, answers, currentChain.resume, modificationCount)
            }
            currentChain = currentChain.resume.apply(answers)
          }
        }
      }
    }
  }
}

/**
 * What a call of a refactoring or a provider gets: the request with the answers so far. The call builds its chain with it. A builder
 * has no result type: [passValue] and [ask] give a chain of any result type, so a helper with another result type takes the same
 * builder, see [ModPlanChain.andThen].
 *
 * **One builder builds one chain.** On each path of the call, use the builder once: call one of [passValue], [ask] and [finish] on it, or
 * give it to one helper, and return that chain. The interpreter runs only the chain that the call returns, so a second chain of the same
 * builder is lost, with its questions. A second use throws [IllegalStateException]. The function after an answer or a value gets a
 * builder of its own.
 *
 * Only the interpreters create a builder.
 */
sealed interface ModPlanChainBuilder {
  /**
   * The input of the call, with the answers so far in its option values. Read the options from it, not from a request that the code
   * captured before a question: only this one holds the answers.
   */
  val request: ModPlanRequest

  /**
   * Ends the chain with its value: the result of the call, or the value of a helper that the chain after it goes on with. Each path
   * of the call ends with one `passValue` or `finish`.
   *
   * @return the chain that ends with [result]
   * @throws IllegalStateException if the builder was used before
   */
  fun <T> passValue(result: T): ModPlanChain<T>

  /**
   * Asks a question, then goes on with [afterAnswers]. The interpreter skips a question that the option values answer, and calls
   * [afterAnswers] at once if it asks nothing. To ask several options in one dialog, give one [ModPlanQuestion.Form] of them.
   *
   * @param question the question, in a dialog of its own; null asks nothing
   * @param afterAnswers goes on after the answer, in a read action; the builder that it gets holds the request with the answers
   * @return the chain that asks [question], then goes on with [afterAnswers]
   * @throws IllegalStateException if the builder was used before
   */
  fun <T> ask(question: ModPlanQuestion?, afterAnswers: Function<ModPlanChainBuilder, ModPlanChain<T>>): ModPlanChain<T>

  /**
   * Ends the chain with [step] as its value: the step is not run here. It goes on as a value, such as to the engine of the refactoring,
   * which collects the steps of its providers, and the caller of the whole chain runs the step of the refactoring at the end, after every
   * question. Put the search and the edits that need no answer into the step, so that they run once, after the user answered everything.
   * The step captures no live PSI, see [ModPlanFinalStep].
   *
   * @return the chain that ends with [step], not run
   * @throws IllegalStateException if the builder was used before
   */
  fun <T> finish(step: ModPlanFinalStep<T>): ModPlanChain<ModPlanFinalStep<T>>
}

/**
 * The last step of a chain: the search and the edits that need no answer, see [ModPlanChainBuilder.finish]. The caller of the whole chain
 * runs it after every question, in a later read action, so it captures no live PSI: only [com.intellij.psi.SmartPsiElementPointer]s,
 * [SymbolPointer]s and values.
 *
 * @param T the result of the step
 */
fun interface ModPlanFinalStep<T> {
  /** @param request the request of the run with every answer: the step runs after the last question */
  @RequiresReadLock
  fun run(request: ModPlanRequest): T
}

/** The only [ModPlanChainBuilder]: it remembers its use, so that it builds one chain. */
private class BuilderImpl(override val request: ModPlanRequest) : ModPlanChainBuilder {
  private var used = false

  private fun use() {
    check(!used) { "A builder builds one chain: it was used before" }
    used = true
  }

  override fun <T> passValue(result: T): ModPlanChain<T> {
    use()
    return ValueChain(request, result)
  }

  override fun <T> ask(question: ModPlanQuestion?, afterAnswers: Function<ModPlanChainBuilder, ModPlanChain<T>>): ModPlanChain<T> {
    use()
    return AskingChain(request, question) { answers -> afterAnswers.apply(builderOf(request, answers)) }
  }

  override fun <T> finish(step: ModPlanFinalStep<T>): ModPlanChain<ModPlanFinalStep<T>> = passValue(step)
}

private fun builderOf(request: ModPlanRequest, answers: ModPlanOptionValues): ModPlanChainBuilder =
  BuilderImpl(ModPlanChain.withOptionValues(request, answers))

/** The end of a chain, with its result. */
private class ValueChain<T>(request: ModPlanRequest, val result: T) : ModPlanChain<T>(request)

/**
 * A question, then the rest of the chain.
 *
 * @property question null goes on with the rest of the chain at once
 * @property resume goes on with the option values that answer the question
 */
private class AskingChain<T>(
  request: ModPlanRequest,
  val question: ModPlanQuestion?,
  val resume: Function<ModPlanOptionValues, ModPlanChain<T>>,
) : ModPlanChain<T>(request)
