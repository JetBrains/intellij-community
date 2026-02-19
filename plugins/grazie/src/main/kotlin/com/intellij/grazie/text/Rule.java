package com.intellij.grazie.text;

import ai.grazie.nlp.langs.Language;
import ai.grazie.rules.settings.Setting;
import com.intellij.grazie.GrazieConfig;
import com.intellij.grazie.utils.TextStyleDomain;
import com.intellij.grazie.utils.TextUtilsKt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URL;
import java.util.List;
import java.util.Objects;

/** A representation of rules from various {@link TextChecker}s. */
public abstract class Rule {
  private final String globalId;
  private final Language language;
  private final String presentableName;
  private final List<String> categories;

  /**
   * Create a rule in a single category
   * @see #Rule(String, Language, String, List)
   */
  public Rule(String globalId, Language language, String presentableName, String category) {
    this(globalId, language, presentableName, List.of(category));
  }

  /**
   * @param globalId a rule identifier that should be as unique as possible.
   *                 For that, it should preferably include some id of its supplying {@link TextChecker}
   *                 and some code of the language that it checks, separated by dots.
   *                 These ids are stored in {@link GrazieConfig.State} when a rule is enabled/disabled manually.
   * @param presentableName the presentable name of the rule
   * @param categories a non-empty list of the presentable names of the rule's categories.
   *                   It's used to group the rules in the settings, starting from the language node.
   */
  public Rule(String globalId, Language language, String presentableName, List<String> categories) {
    this.globalId = globalId;
    this.language = language;
    this.presentableName = presentableName;
    this.categories = List.copyOf(categories);
    if (categories.isEmpty()) {
      throw new IllegalArgumentException("There should be at least one category specified for rule " + globalId);
    }
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
   * @return the language that the rule is applicable to
   */
  public Language getLanguage() {
    return language;
  }

  /**
   * @return a text/HTML description of what the rule does, to be displayed in settings
   */
  public abstract @NotNull String getDescription();

  /**
   * @return a description text to honor while searching the rule settings
   */
  public @NotNull String getSearchableDescription() {
    return getDescription();
  }

  /**
   * An optional URL describing the rule match in more detail.
   * Typically, it points to a dictionary or grammar website with explanations and examples.
   */
  public @Nullable URL getUrl() {
    return null;
  }

  /**
   * @return a non-empty list consisting of presentable names of the rule's categories:
   * the nodes it should be placed into in the Rule settings tree, starting from the language node
   */
  public List<String> getCategories() {
    return categories;
  }

  /**
   * @return whether this rule is enabled by default
   *
   * @deprecated Use {@link #isEnabledByDefault(TextStyleDomain)} instead.
   */
  @Deprecated(forRemoval = true)
  public boolean isEnabledByDefault() {
    return true;
  }

  /**
   * @return whether this rule is enabled by default in the given domain
   */
  public boolean isEnabledByDefault(TextStyleDomain domain) {
    return true;
  }

  /**
   * @return an optional featured rule setting to open from "Rule X settings" quick fix.
   */
  public @Nullable Setting getFeaturedSetting() {
    return null;
  }

  public final boolean isCurrentlyEnabled(TextContent content) {
    GrazieConfig.State state = GrazieConfig.Companion.get();
    TextStyleDomain domain = TextUtilsKt.getTextDomain(content);
    return isEnabledInState(state, domain);
  }

  public final boolean isEnabledInState(GrazieConfig.State state, TextStyleDomain domain) {
    return isEnabledByDefault(domain) ? !state.isRuleDisabled(globalId, domain)
                                      : state.isRuleEnabled(globalId, domain);
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
