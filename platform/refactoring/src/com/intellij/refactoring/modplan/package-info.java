// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
/**
 * ModPlan: refactorings that find their changes without UI, write them on non-physical copies, and apply them in one command. The
 * safe delete is the first one: {@code com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteRefactoring}.
 * <h2>Flow</h2>
 * <ol>
 *   <li>A caller creates a {@link com.intellij.refactoring.modplan.ModPlanRequest}: the project, the subjects and the option values.</li>
 *   <li>{@link com.intellij.refactoring.modplan.ModPlanRefactoring#chain} gives a {@link com.intellij.refactoring.modplan.ModPlanChain}:
 *   its questions, then the {@link com.intellij.refactoring.modplan.ModPlanFinalStep} that creates the plan.</li>
 *   <li>An interpreter runs the chain and answers its questions. An answer gives a new request: the option values of the request
 *   with the value of the answer, see {@link com.intellij.refactoring.modplan.ModPlanChain#withOptionValues}; the rest of the chain
 *   reads the new request. A question that the option values already answer is not asked: MCP and LSP set the options of a request
 *   before the run.
 *   <ul>
 *     <li>the IDE: {@code ModPlanUiInterpreter}, with dialogs;</li>
 *     <li>a quick fix, an intention, LSP: {@link com.intellij.refactoring.modplan.ModPlanCommandInterpreter#interpret}, each question
 *     is a {@code ModCommand};</li>
 *     <li>headless, MCP and tests: {@link com.intellij.refactoring.modplan.ModPlanRefactoring#plan}, through
 *     {@link com.intellij.refactoring.modplan.ModPlanCommandInterpreter#interpretHeadless}; the option values answer every question.</li>
 *   </ul></li>
 *   <li>After the last question, {@link com.intellij.refactoring.modplan.ModPlanFinalStep#run} takes the request with every answer. The
 *   providers give their analyses: the edits, a list of {@link com.intellij.refactoring.modplan.ModPlanEdit}, and the conflicts. The edits describe the change and write
 *   nothing.</li>
 *   <li>{@link com.intellij.refactoring.modplan.ModPlan.Companion#createPlan} converts the edits:
 *   {@link com.intellij.refactoring.modplan.ModPlan.Companion#toCommand} runs them in their order on non-physical copies and gives
 *   the file writes as one {@code ModCommand}. The result is a {@link com.intellij.refactoring.modplan.ModPlan}:
 *   {@code Ready(conflicts, execution)}, or {@code Refusal(reason, location)}, also if an edit cannot be written.</li>
 *   <li>The user agrees to the conflicts, and the execution runs: {@link com.intellij.refactoring.modplan.ModPlanExecutor#execute} in the
 *   IDE and headless, one command and one undo step; {@code ModCommand.showConflicts(...).andThen(execution.getCommand())} for a
 *   {@code ModCommand}.</li>
 * </ol>
 * <ul>
 *   <li>A setting of a refactoring is a {@link com.intellij.refactoring.modplan.ModPlanOption}, read from the immutable
 *   {@link com.intellij.refactoring.modplan.ModPlanOptionValues} of the request; MCP and LSP set the options by id.</li>
 *   <li>The subjects are {@link com.intellij.model.SymbolPointer}s, so a request outlives read actions
 *   and dialogs. The code after a question runs later, in another read action, and captures no live PSI.</li>
 *   <li>A conflict is a non-code usage (left as it is), a fixable conflict (the edits fix it) or a broken conflict (it stays). A
 *   refusal is not a conflict: the refactoring cannot run.</li>
 *   <li>The legacy flow is the refactoring on {@code BaseRefactoringProcessor}; ModPlan gives the same result.</li>
 * </ul>
 */
@ApiStatus.Experimental
package com.intellij.refactoring.modplan;

import org.jetbrains.annotations.ApiStatus;
