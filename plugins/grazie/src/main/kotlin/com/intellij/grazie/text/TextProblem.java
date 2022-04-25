package com.intellij.grazie.text;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.TextRange;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.text.StringOperation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/** A problem found by a {@link TextChecker} in natural language text */
public abstract class TextProblem {
  private final Rule rule;
  private final TextContent text;
  private final List<TextRange> highlightRanges;

  protected TextProblem(@NotNull Rule rule, @NotNull TextContent text, @NotNull TextRange highlightRange) {
    this(rule, text, List.of(highlightRange));
  }

  protected TextProblem(@NotNull Rule rule, @NotNull TextContent text, @NotNull List<TextRange> highlightRanges) {
    this.rule = rule;
    this.text = text;
    this.highlightRanges = Collections.unmodifiableList(highlightRanges);
    assert !highlightRanges.isEmpty();
    for (int i = 0; i < highlightRanges.size(); i++) {
      TextRange range = highlightRanges.get(i);
      assert range.getStartOffset() >= 0 && range.getEndOffset() <= text.length();
      if (i > 0) {
        assert range.getStartOffset() > highlightRanges.get(i - 1).getEndOffset();
      }
    }
  }

  /** @return the rule that triggered this problem */
  public final @NotNull Rule getRule() {
    return rule;
  }

  /** @return a short message describing the problem, to be shown in quick fix names */
  public abstract @NotNull String getShortMessage();

  /**
   * @return the text/HTML for {@link ProblemDescriptor#getDescriptionTemplate()}, to be shown in the status bar and Inspections view
   * */
  public abstract @NotNull @InspectionMessage String getDescriptionTemplate(boolean isOnTheFly);

  /**
   * @return the text/HTML for {@link ProblemDescriptor#getTooltipTemplate()}
   * */
  public @NotNull @NlsContexts.Tooltip String getTooltipTemplate() {
    return getDescriptionTemplate(true);
  }

  /** @return the underlying text content where this problem was found */
  public final @NotNull TextContent getText() {
    return text;
  }

  /**
   * @return the range in {@link #getText()} to be highlighted
   * @deprecated use {@link #getHighlightRanges()}
   */
  @Deprecated(forRemoval = true)
  public final @NotNull TextRange getHighlightRange() {
    return new TextRange(highlightRanges.get(0).getStartOffset(), ContainerUtil.getLastItem(highlightRanges).getEndOffset());
  }

  /** @return the ranges in {@link #getText()} to be highlighted, non-intersecting, sorted by the start offset ascending */
  public final @NotNull List<TextRange> getHighlightRanges() {
    return highlightRanges;
  }

  /**
   * @return the range in {@link #getText()} to be replaced with {@link #getCorrections()}
   * @deprecated use {@link #getSuggestions()} instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public @NotNull TextRange getReplacementRange() {
    return getHighlightRanges().get(0);
  }

  /**
   * @return the range in {@link #getText()} used by the rule to perform the check.
   * By default, it's {@code null}, and then the whole sentence is considered to affect the rule results.
   * This information can be used to suppress or ignore certain problems.
   */
  public @Nullable TextRange getPatternRange() {
    return null;
  }

  /**
   * @return a list of suggested corrections for this problem, all applied to {@link #getReplacementRange()}.
   * @deprecated use {@link #getSuggestions()} instead
   */
  @SuppressWarnings("DeprecatedIsStillUsed")
  @Deprecated
  public @NotNull List<String> getCorrections() {
    return List.of();
  }

  /** @return a list of correction suggestions for this problem */
  public @NotNull List<Suggestion> getSuggestions() {
    List<String> corrections = getCorrections();
    if (corrections.isEmpty()) return List.of();

    TextRange range = getReplacementRange();
    return ContainerUtil.map(corrections, replacement -> Suggestion.replace(range, replacement));
  }

  /** Return a list of quick fixes to display under {@link #getCorrections} suggestions */
  public @NotNull List<LocalQuickFix> getCustomFixes() {
    return Collections.emptyList();
  }

  /**
   * @return whether this problem is subject to the given rule group.
   * By default, it's checked that the rule's global ID is present in the group.
   * Note that some IDs in the group might represent abstract categories
   * (e.g. missing punctuation or sentence start capitalization),
   * not bound to any specific rule at all.
   * In this case, specific implementations can check that their own rules belong to these categories
   * and still return {@code true}.
   */
  public boolean fitsGroup(@NotNull RuleGroup group) {
    return group.getRules().contains(rule.getGlobalId());
  }

  @Override
  public String toString() {
    return getHighlightRange().subSequence(text) + " (" + getShortMessage() + ")";
  }

  public interface Suggestion {
    /** The list of non-intersecting changes to be performed, with the ranges and texts referring to {@link TextContent} text */
    List<StringOperation> getChanges();

    /** The text to show in the context action popup */
    String getPresentableText();

    /** Create a suggestion for a single replacement operation in the given range */
    static Suggestion replace(TextRange range, CharSequence replacement) {
      return new Suggestion() {
        @Override
        public List<StringOperation> getChanges() {
          return List.of(StringOperation.replace(range, replacement));
        }

        @Override
        public String getPresentableText() {
          return replacement.toString();
        }
      };
    }
  }
}
