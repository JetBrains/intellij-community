package com.intellij.usages.rules;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * {@link UsageGroupingRule} that provides self-description
 */
public interface UsageGroupingRuleEx extends UsageGroupingRule {
  /**
   * Should return unique ID of this rule used to distinguish it from other types of rules
   */
  default @NonNls @NotNull String getId() { return getClass().getName(); }

  /**
   * User-visible icon for this rule, used on grouping toggle button (if grouping toolbar is used)
   */
  default @Nullable Icon getIcon() { return null; }

  /**
   * User-visible name of this rule, used on grouping toggle button if grouping action is automatically created
   */
  default @NotNull String getTitle() { return getClass().getSimpleName(); }

  /**
   * ID for grouping action that controls this grouping rule
   * null indicates that an action should be created automatically
   */
  default @NonNls @Nullable String getGroupingActionId() { return null; }

  /**
   * If true, this grouping rule is enabled in inverse compared to its grouping action
   */
  default boolean isGroupingActionInverted() { return false; }

  /**
   * The grouping rule can be toggled if and only if this returns true.
   * If this returns false, the rule is always active.
   */
  default boolean isGroupingToggleable() { return true; }
}
