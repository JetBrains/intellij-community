// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import com.intellij.codeInsight.intention.CommonIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ex.QuickFixWrapper;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.modcommand.*;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.Assert.fail;

/**
 * An object representing a wanted assertion of given quick-fix test file.
 *
 * @author Tagir Valeev
 */
public final class ActionHint {
  private final @NotNull String myExpectedText;
  private final boolean myShouldPresent;
  private final ProblemHighlightType myHighlightType;
  private final boolean myExactMatch;
  private final boolean myCheckPreview;

  private ActionHint(@NotNull String expectedText, boolean shouldPresent, ProblemHighlightType highlightType, boolean exactMatch,
                     boolean preview) {
    myExpectedText = expectedText;
    myShouldPresent = shouldPresent;
    myHighlightType = highlightType;
    myExactMatch = exactMatch;
    myCheckPreview = preview;
  }

  /**
   * Returns an expected text of the action.
   * <p>
   * Usage of this method is discouraged: it's not guaranteed that ActionHint actually looks for text.
   * </p>
   *
   * @return an expected action text. May throw an {@link IllegalStateException} if this ActionHint expects something else
   * (e.g. quick-fix of specific type, etc.)
   */
  public @NotNull String getExpectedText() {
    return myExpectedText;
  }

  /**
   * @return true if this ActionHint checks that some action should be present
   * or false if it checks that some action should be absent
   */
  @SuppressWarnings("WeakerAccess") // used in kotlin
  public boolean shouldPresent() {
    return myShouldPresent;
  }

  /**
   * @return true if generated intention preview should be checked
   */
  public boolean shouldCheckPreview() {
    return myCheckPreview;
  }

  /**
   * Finds the action which matches this ActionHint and returns it or returns null
   * if this ActionHint asserts that no action should be present.
   * <p>
   * Use {@link #findAndCheck(Collection, ActionContext, Supplier)} if you expect multistep action 
   *
   * @param actions actions collection to search inside
   * @param infoSupplier a supplier which provides additional info which will be appended to exception message if check fails
   * @return the action or null
   * @throws AssertionError if no action is found, but it should present, or if action is found, but it should not present.
   */
  public @Nullable IntentionAction findAndCheck(@NotNull Collection<? extends IntentionAction> actions, @NotNull Supplier<String> infoSupplier) {
    return findAndCheck(actions, null, infoSupplier);
  }

  /**
   * Finds the action which matches this ActionHint and returns it or returns null
   * if this ActionHint asserts that no action should be present.
   *
   * @param actions      actions collection to search inside
   * @param context      action execution context
   * @param infoSupplier a supplier which provides additional info which will be appended to exception message if check fails
   * @return the action or null
   * @throws AssertionError if no action is found, but it should present, or if action is found, but it should not present.
   */
  public @Nullable IntentionAction findAndCheck(@NotNull Collection<? extends IntentionAction> actions,
                                                @Nullable ActionContext context,
                                                @NotNull Supplier<String> infoSupplier) {
    String[] steps = myExpectedText.split("\\|->");
    CommonIntentionAction found = null;
    Collection<? extends CommonIntentionAction> commonActions = actions;
    for (int i = 0; i < steps.length; i++) {
      String curStep = steps[i];
      found = ContainerUtil.find(commonActions, t -> {
        String text = getActionText(context, t);
        return myExactMatch ? text.equals(curStep) : text.startsWith(curStep);
      });
      if (i == steps.length - 1) break;
      if (context == null) {
        fail("Action context is not supplied");
      }
      if (found == null) {
        fail(exceptionHeader(curStep) + " not found\nAvailable actions: " +
             commonActions.stream().map(act -> getActionText(context, act)).collect(Collectors.joining(", ", "[", "]\n")) +
             infoSupplier.get());
      }
      ModCommandAction action = found.asModCommandAction();
      if (action == null) {
        fail(exceptionHeader(curStep) + " is not ModCommandAction");
      }
      ModCommand command = action.perform(context);
      if (!(command instanceof ModChooseAction chooseAction)) {
        if (myShouldPresent) {
          fail(exceptionHeader(curStep) + " does not produce a chooser");
        }
        return null;
      }
      commonActions = chooseAction.actions();
    }
    IntentionAction result = found == null ? null : found.asIntention();
    String lastStep = steps[steps.length - 1];
    if (myShouldPresent) {
      if (result == null) {
        fail(exceptionHeader(lastStep) + " not found\nAvailable actions: " +
             commonActions.stream()
               .filter(ca -> !(ca instanceof ModCommandAction mca) || context != null && mca.getPresentation(context) != null)
               .map(ca -> {
                 return ca instanceof ModCommandAction mca ? Objects.requireNonNull(mca.getPresentation(context)).name() :
                        ca.asIntention().getText();
               }).collect(Collectors.joining(", ", "[", "]\n")) +
             infoSupplier.get());
      }
      else if (myHighlightType != null) {
        result = IntentionActionDelegate.unwrap(result);
        ProblemHighlightType actualType = QuickFixWrapper.getHighlightType(result);
        if (actualType == null) {
          fail(exceptionHeader(lastStep) + " is not a LocalQuickFix, but " + result.getClass().getName() +
               "\nExpected LocalQuickFix with ProblemHighlightType=" + myHighlightType + "\n" +
               infoSupplier.get());
        }
        if (actualType != myHighlightType) {
          fail(exceptionHeader(lastStep) + " has wrong ProblemHighlightType.\nExpected: " + myHighlightType +
               "\nActual: " + actualType + "\n" + infoSupplier.get());
        }
      }
    }
    else if(result != null) {
      fail(exceptionHeader(lastStep) + " is present, but should not\n" + infoSupplier.get());
    }
    return result;
  }

  private static @NotNull String getActionText(@Nullable ActionContext context, CommonIntentionAction t) {
    if (t instanceof IntentionAction intention) {
      return intention.getText();
    }
    if (!(t instanceof ModCommandAction action)) {
      throw new AssertionError("Action is not ModCommandAction: " + t);
    }
    if (context == null) {
      fail("Context is not specified for ModCommandAction");
    }
    Presentation presentation = action.getPresentation(context);
    return presentation == null ? "(unavailable) " + action.getFamilyName() : presentation.name();
  }

  private @NotNull String exceptionHeader(String text) {
    return "Action with " + (myExactMatch ? "text" : "prefix") + " '" + text + "'";
  }

  public static @NotNull ActionHint parse(@NotNull PsiFile file, @NotNull String contents) {
    return parse(file, contents, true);
  }

  /**
   * Parse given file with given contents extracting ActionHint of it.
   * <p>
   * Currently the following syntax is supported:
   * </p>
   * {@code // "quick-fix name or intention text[|->next step]" "true|false|<ProblemHighlightType>[-preview]"}
   * <p>
   * (replace // with line comment prefix in the corresponding language if necessary).
   * If {@link ProblemHighlightType} enum value is specified instead of true/false
   * (e.g. {@code "INFORMATION"}), then
   * it's expected that the action is present and it's a quick-fix with given highlight type.
   * </p>
   *
   * @param file PsiFile associated with contents (used to determine the language)
   * @param contents file contents
   * @param exactMatch if false then action hint matches prefix like in {@link CodeInsightTestFixture#filterAvailableIntentions(String)}
   * @return ActionHint object
   * @throws AssertionError if action hint is absent or has invalid format
   */
  public static @NotNull ActionHint parse(@NotNull PsiFile file, @NotNull String contents, boolean exactMatch) {
    PsiFile hostFile = InjectedLanguageManager.getInstance(file.getProject()).getTopLevelFile(file);

    final Commenter commenter = LanguageCommenters.INSTANCE.forLanguage(hostFile.getLanguage());
    String comment = commenter.getLineCommentPrefix();
    if (comment == null) {
      comment = commenter.getBlockCommentPrefix();
    }

    assert comment != null : commenter;
    return parse(file, contents, Pattern.quote(comment), exactMatch);
  }

  /**
   * Parse given file with given contents extracting ActionHint of it.
   * <p>
   * Currently the following syntax is supported:
   * </p>
   * {@code $commentPrefix "quick-fix name or intention text[|->next step]" "true|false|<ProblemHighlightType>[-preview]"}
   * <p>
   * If {@link ProblemHighlightType} enum value is specified instead of true/false
   * (e.g. {@code "INFORMATION"}), then
   * it's expected that the action is present and it's a quick-fix with given highlight type.
   * </p>
   *
   * @param file PsiFile associated with contents (used to determine the language)
   * @param contents file contents
   * @param commentPrefix any custom specific prefix, could be a regexp
   * @param exactMatch if false then action hint matches prefix like in {@link CodeInsightTestFixture#filterAvailableIntentions(String)}
   * @return ActionHint object
   * @throws AssertionError if action hint is absent or has invalid format
   */
  public static @NotNull ActionHint parse(@NotNull PsiFile file, @NotNull String contents, @NotNull String commentPrefix, boolean exactMatch) {
    // $commentPrefix "quick fix action text to perform" "should be available"
    Pattern pattern = Pattern.compile("^" + commentPrefix + " \"([^\n]*)\" \"(\\w+)(?:-(\\w+))?\".*", Pattern.DOTALL);
    Matcher matcher = pattern.matcher(contents);
    TestCase.assertTrue("No comment found in " + file.getVirtualFile(), matcher.matches());
    final String text = matcher.group(1);
    String state = matcher.group(2);
    String previewState = matcher.group(3);
    boolean checkPreview = "preview".equals(previewState);
    if(state.equals("true") || state.equals("false")) {
      return new ActionHint(text, Boolean.parseBoolean(state), null, exactMatch, checkPreview);
    }
    return new ActionHint(text, true, ProblemHighlightType.valueOf(state), exactMatch, checkPreview);
  }
}
