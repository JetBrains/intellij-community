// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @see SimpleChangesGroupingPolicy as base class suitable for most implementations.
 */
public interface ChangesGroupingPolicy {
  /**
   * @deprecated Implement {@link #getParentNodeFor(StaticFilePath, ChangesBrowserNode, ChangesBrowserNode)} instead.
   */
  @Deprecated
  default @Nullable ChangesBrowserNode<?> getParentNodeFor(@NotNull StaticFilePath nodePath,
                                                 @NotNull ChangesBrowserNode<?> subtreeRoot) {
    return getParentNodeFor(nodePath, new CompatibilityPlaceholderChangesBrowserNode(), subtreeRoot);
  }

  default @Nullable ChangesBrowserNode<?> getParentNodeFor(@NotNull StaticFilePath nodePath,
                                                           @NotNull ChangesBrowserNode<?> node,
                                                           @NotNull ChangesBrowserNode<?> subtreeRoot) {
    return getParentNodeFor(nodePath, subtreeRoot);
  }

  default void setNextGroupingPolicy(@Nullable ChangesGroupingPolicy policy) {
    if (policy != null) {
      Logger.getInstance(ChangesGroupingPolicy.class).warn(String.format("Next grouping policy is ignored: %s in %s", policy, this));
    }
  }

  /**
   * If we have a plugin-provided legacy {@link ChangesGroupingPolicy} implementation
   * via {@link ChangesGroupingPolicy#getParentNodeFor(StaticFilePath, ChangesBrowserNode)},
   * we lose information about currently grouped node.
   * <p>
   * This node will be passed as a substitute and can be used with {@code instanceOf} check to enable fallback route if needed.
   */
  class CompatibilityPlaceholderChangesBrowserNode extends ChangesBrowserNode<Object> {
    private static final Object PLACEHOLDER_NODE_VALUE = new Object();

    CompatibilityPlaceholderChangesBrowserNode() {
      super(PLACEHOLDER_NODE_VALUE);
    }

    @Override
    public void render(@NotNull ChangesBrowserNodeRenderer renderer, boolean selected, boolean expanded, boolean hasFocus) {
      renderer.append("Placeholder.text");
      renderer.setIcon(AllIcons.FileTypes.Any_type);
    }

    @Override
    public String toString() {
      return "PlaceholderChangesBrowserNode";
    }
  }
}