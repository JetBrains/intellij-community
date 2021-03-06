// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl;

import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

class UsageTargetNode extends Node {
  UsageTargetNode(@NotNull UsageTarget target) {
    setUserObject(target);
  }

  @Override
  public String tree2string(int indent, @NotNull String lineSeparator) {
    return getTarget().getName();
  }

  @Override
  protected boolean isDataValid() {
    return getTarget().isValid();
  }

  @Override
  protected boolean isDataReadOnly() {
    return getTarget().isReadOnly();
  }

  @Override
  protected boolean isDataExcluded() {
    return false;
  }

  @NotNull
  @Override
  protected String getText(@NotNull final UsageView view) {
    return ObjectUtils.notNull(getTarget().getPresentation().getPresentableText(), "");
  }

  @NotNull
  public UsageTarget getTarget() {
    return (UsageTarget)getUserObject();
  }

  @Override
  protected void updateNotify() {
    super.updateNotify();
    getTarget().update();
  }
}
