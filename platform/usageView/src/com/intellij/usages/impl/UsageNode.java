// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;
import com.intellij.util.DeprecatedMethodException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

public class UsageNode extends Node implements Comparable<UsageNode>, Navigatable {
  /**
   * @deprecated use {@link #UsageNode(Node, Usage)} instead
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval(inVersion = "2018.1")
  // todo remove in 2018.1
  public UsageNode(@NotNull Usage usage, UsageViewTreeModelBuilder model) {
    this(null, usage);
    DeprecatedMethodException.report("Use UsageNode(Node, Usage) instead");
  }

  public UsageNode(Node parent, @NotNull Usage usage) {
    setUserObject(usage);
    setParent(parent);
  }

  @Override
  public String toString() {
    return getUsage().toString();
  }

  @Override
  public String tree2string(int indent, String lineSeparator) {
    StringBuffer result = new StringBuffer();
    StringUtil.repeatSymbol(result, ' ', indent);
    result.append(getUsage());
    return result.toString();
  }

  @Override
  public int compareTo(@NotNull UsageNode usageNode) {
    return UsageViewImpl.USAGE_COMPARATOR.compare(getUsage(), usageNode.getUsage());
  }

  @NotNull
  public Usage getUsage() {
    return (Usage)getUserObject();
  }

  @Override
  public void navigate(boolean requestFocus) {
    getUsage().navigate(requestFocus);
  }

  @Override
  public boolean canNavigate() {
    return getUsage().isValid() && getUsage().canNavigate();
  }

  @Override
  public boolean canNavigateToSource() {
    return getUsage().isValid() && getUsage().canNavigate();
  }

  @Override
  protected boolean isDataValid() {
    return getUsage().isValid();
  }

  @Override
  protected boolean isDataReadOnly() {
    return getUsage().isReadOnly();
  }

  @Override
  protected boolean isDataExcluded() {
    return isExcluded();
  }

  @NotNull
  @Override
  protected String getText(@NotNull final UsageView view) {
    return getUsage().getPresentation().getPlainText();
  }

  @Override
  protected void updateCachedPresentation() {
    getUsage().getPresentation().updateCachedText();
  }
}
