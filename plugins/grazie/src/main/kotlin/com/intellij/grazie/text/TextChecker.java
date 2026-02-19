package com.intellij.grazie.text;

import ai.grazie.nlp.langs.Language;
import com.intellij.grazie.GrazieConfig;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * A way of checking text correctness according to some set of rules.
 */
public abstract class TextChecker {
  private static final ExtensionPointName<TextChecker> EP = new ExtensionPointName<>("com.intellij.grazie.textChecker");

  public static List<TextChecker> allCheckers() {
    return EP.getExtensionList();
  }

  /**
   * @return all the UI-configurable rules this checker provides in the language determined by the given locale,
   * possibly taking region into account.
   */
  public abstract @NotNull Collection<? extends Rule> getRules(@NotNull Locale locale);

  /**
   * @deprecated Implement and use {@link #check(ProofreadingContext)} instead.
   */
  @Deprecated(forRemoval = true)
  public abstract @NotNull Collection<? extends TextProblem> check(@NotNull TextContent extracted);

  /**
   * Perform the checks on the given context.
   * The implementations should check {@link GrazieConfig.State} for enabled/disabled rules.
   */
  public @NotNull Collection<? extends TextProblem> check(@NotNull ProofreadingContext context) {
    return check(context.getText());
  }

  @ApiStatus.Experimental
  public interface ProofreadingContext {
    /**
     * Returns text extracted by TextExtractor in a given language
     */
    @NotNull TextContent getText();

    /**
     * Returns the language of the text or [Language.UNKNOWN] if one can't be determined.
     */
    @NotNull Language getLanguage();

    /**
     * Returns the prefix that should be stripped from the text before checking, most often an empty string.
     * <p>
     * For example, commit messages often start with a tracker issue ID or a subsystem name, which usually shouldn't be checked for grammar.
     */
    @NotNull String getStripPrefix();
  }
}
