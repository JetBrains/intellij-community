// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan;

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;

/**
 * A refactoring as a {@link ModCommandAction}, for a quick fix, an intention or a code action of LSP.
 * The action searches nothing before it runs: {@link #getPresentation} checks only that the
 * refactoring supports the request, and the preview shows the text of the action.
 * <p>
 * <b>Known differences from the legacy flow:</b>
 * <ul>
 *   <li>the non-code usages are shown in the conflicts dialog, where the legacy flow shows them in the usage view;</li>
 *   <li>the command publishes no refactoring event and makes no local history label; the undo of the changes in several files is not
 *   checked;</li>
 *   <li>the questions start with the values that the storages of the questions remember, if the action loads them; the answers are not
 *   stored;</li>
 *   <li>batch execution and LSP take the initial values, and the options of a custom question keep the values of the request;</li>
 *   <li>a client of LSP that cannot show the conflicts goes on, and a message lists the conflicts and the non-code usages;</li>
 *   <li>the search runs again if PSI changed while a dialog was open;</li>
 *   <li>the preview shows the text of the action, not the usages or the result;</li>
 *   <li>a guest of Code With Me gets an error for a question, as the dialogs of a command are not shown to a guest;</li>
 *   <li>the action is not offered for an element that the refactoring does not support, where a legacy fix runs the legacy flow;</li>
 *   <li>as a {@code LocalQuickFix}, the action shows its family name in the inspection results, not its text;</li>
 *   <li>in the unit-test mode nothing is asked, see {@link ModPlanCommandInterpreter#ASKS_IN_TESTS}.</li>
 * </ul>
 * <p>
 * A subclass keeps the name of the class of a fix, and may name the family and show its own preview.
 */
public class ModPlanCommandAction implements ModCommandAction {
  private final @NotNull ModPlanRefactoring myRefactoring;
  private final @NotNull ModPlanRequest myRequest;
  private final @NotNull SmartPsiElementPointer<PsiElement> myAnchor;
  private final @NotNull @IntentionName String myText;
  private final boolean myLoadsRememberedValues;

  /**
   * An action whose questions start with the values that their storages remember.
   *
   * @param request the request of the refactoring; its options are the answers known up front
   * @param anchor  the element the action is invoked on; a conflict with no place of its own is shown at it
   * @param text    the text of the action, which names the change, such as "Safe delete 'foo'"
   */
  @RequiresReadLock
  public ModPlanCommandAction(@NotNull ModPlanRefactoring refactoring,
                              @NotNull ModPlanRequest request,
                              @NotNull PsiElement anchor,
                              @NotNull @IntentionName String text) {
    this(refactoring, request, anchor, text, true);
  }

  /**
   * @param loadsRememberedValues whether the questions start with the values that their storages remember; false starts them with the
   *                              option values of the request, or the defaults of the options, as LSP needs
   * @see #ModPlanCommandAction(ModPlanRefactoring, ModPlanRequest, PsiElement, String)
   */
  @RequiresReadLock
  public ModPlanCommandAction(@NotNull ModPlanRefactoring refactoring,
                              @NotNull ModPlanRequest request,
                              @NotNull PsiElement anchor,
                              @NotNull @IntentionName String text,
                              boolean loadsRememberedValues) {
    myRefactoring = refactoring;
    myRequest = request;
    myAnchor = SmartPointerManager.createPointer(anchor);
    myText = text;
    myLoadsRememberedValues = loadsRememberedValues;
  }

  /** @return the element the action is invoked on; null if it is gone */
  protected final @Nullable PsiElement getAnchor() {
    return myAnchor.getElement();
  }

  @Override
  @SuppressWarnings("DialogTitleCapitalization")
  public @NotNull String getFamilyName() {
    return myRefactoring.title();
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    if (getAnchor() == null || !myRefactoring.isSupported(myRequest)) return null;
    return Presentation.of(myText);
  }

  @Override
  public final @NotNull ModCommand perform(@NotNull ActionContext context) {
    if (getAnchor() == null) return ModCommand.nop();
    Function<ModPlanChainBuilder, ModPlanChain<ModCommand>> chainFunction = chainBuilder ->
      myRefactoring.chain(chainBuilder)
        .andThen((afterPlan, planStep) -> {
          // The step of the plan runs here, at the end of the chain, after every question.
          ModPlan plan = planStep.run(afterPlan.getRequest());
          PsiElement currentAnchor = getAnchor();
          if (!(plan instanceof ModPlan.AcceptedPlan readyPlan) || currentAnchor == null) {
            return afterPlan.passValue(ModCommand.error(RefactoringBundle.message("cannot.perform.refactoring")));
          }
          return afterPlan.passValue(ModCommand.showConflicts(
              readyPlan.getConflicts().toShowConflictsWithNonCodeUsages(currentAnchor))
                                       .andThen(readyPlan.getExecution().getCommand()));
        });
    return ModPlanCommandInterpreter.interpret(myRefactoring.title(), myRequest, myLoadsRememberedValues, chainFunction);
  }


  /** The default preview runs {@link #perform}, which is the whole search. */
  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull ActionContext context) {
    return new IntentionPreviewInfo.Html(HtmlChunk.text(myText));
  }
}
