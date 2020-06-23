package com.intellij.usages.rules;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * {@link UsageGroupingRule} that provides self-description
 */
public interface UsageGroupingRuleEx extends UsageGroupingRule {
  /**
   * Should return unique ID of this rule used to distinguish it from other types of rules
   */
  @NotNull String getId();

  /**
   * User-visible icon for this rule, used on grouping toggle button
   */
  @NotNull Icon getIcon();

  /**
   * User-visible name of this rule, used on grouping toggle button
   */
  @NotNull String getTitle();
}
