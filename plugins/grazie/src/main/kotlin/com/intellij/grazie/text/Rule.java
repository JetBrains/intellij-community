package com.intellij.grazie.text;

import com.intellij.grazie.GrazieConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.Objects;

/** A representation of rules from various {@link TextChecker}s. */
public abstract class Rule {
  private final String globalId;
  private final String presentableName;
  private final String category;
  private boolean enabledByDefault = true;

  /**
   * @deprecated use {@link #Rule(String, String, String)}
   */
  @Deprecated
  public Rule(String globalId, String presentableName, String category, boolean enabledByDefault) {
    this(globalId, presentableName, category);
    this.enabledByDefault = enabledByDefault;
    if (!globalId.contains(".")) {
      throw new IllegalArgumentException("Global id should be a qualified name with at least one dot inside: " + this);
    }
  }

  /**
   * @param globalId a rule identifier that should be as unique as possible.
   *                 For that, it should preferably include some id of its supplying {@link TextChecker}
   *                 and some code of the language that it checks, separated by dots.
   *                 These ids are stored in {@link GrazieConfig.State} when a rule is enabled/disabled manually.
   * @param presentableName the presentable name of the rule
   * @param category the presentable name of the rule's category, to group the rules in the settings
   */
  public Rule(String globalId, String presentableName, String category) {
    this.globalId = globalId;
    this.presentableName = presentableName;
    this.category = category;
    if (!globalId.contains(".")) {
      throw new IllegalArgumentException("Global id should be a qualified name with at least one dot inside: " + this);
    }
  }

  public String getGlobalId() {
    return globalId;
  }

  public String getPresentableName() {
    return presentableName;
  }

  /**
   * @return a text/HTML description of what the rule does, to be displayed in settings
   */
  public abstract @NotNull String getDescription();

  /**
   * An optional URL describing the rule match in more detail.
   * Typically, it points to a dictionary or grammar website with explanations and examples.
   */
  public @Nullable URL getUrl() {
    return null;
  }

  /** @return the presentable name of the rule's category */
  public final String getCategory() {
    return category;
  }

  /**
   * @return whether this rule is enabled by default
   */
  public boolean isEnabledByDefault() {
    return enabledByDefault;
  }

  @SuppressWarnings("unused")
  public final boolean isCurrentlyEnabled() {
    return isEnabledInState(GrazieConfig.Companion.get());
  }

  public final boolean isEnabledInState(GrazieConfig.State state) {
    return isEnabledByDefault() ? !state.getUserDisabledRules().contains(globalId)
                                : state.getUserEnabledRules().contains(globalId);
  }

  @Override
  public final boolean equals(Object o) {
    return this == o || getClass().equals(o.getClass()) && globalId.equals(((Rule) o).globalId);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(globalId);
  }

  @Override
  public String toString() {
    return presentableName + "(" + globalId + ")";
  }
}
