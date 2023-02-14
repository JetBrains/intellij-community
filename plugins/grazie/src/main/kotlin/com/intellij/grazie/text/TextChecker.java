package com.intellij.grazie.text;

import com.intellij.grazie.GrazieConfig;
import com.intellij.openapi.extensions.ExtensionPointName;
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
   * Perform the checks on the given text content.
   * The implementations should check {@link GrazieConfig.State} for enabled/disabled rules.
   */
  public abstract @NotNull Collection<? extends TextProblem> check(@NotNull TextContent extracted);

}
