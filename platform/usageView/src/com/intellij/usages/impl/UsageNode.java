/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.usages.impl;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.pom.Navigatable;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageView;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class UsageNode extends Node implements Comparable<UsageNode>, Navigatable {
  @Deprecated
  // todo remove in 2018.1
  public UsageNode(@NotNull Usage usage, UsageViewTreeModelBuilder model) {
    this(null, usage);
  }

  public UsageNode(Node parent, @NotNull Usage usage) {
    setUserObject(usage);
    setParent(parent);
  }

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
