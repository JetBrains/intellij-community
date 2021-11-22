// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.pom.Navigatable;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageNodePresentation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UsageNode extends Node implements Comparable<UsageNode>, Navigatable {
  public UsageNode(Node parent, @NotNull Usage usage) {
    setUserObject(usage);
    setParent(parent);
  }

  @Override
  public String toString() {
    return getUsage().toString();
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
  protected String getNodeText() {
    return getUsage().getPresentation().getPlainText();
  }

  @Override
  public @Nullable UsageNodePresentation getCachedPresentation() {
    return getUsage().getPresentation().getCachedPresentation();
  }

  @Override
  protected void updateCachedPresentation() {
    getUsage().getPresentation().updateCachedPresentation();
  }
}
