// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.impl;

import com.intellij.usages.UsageTarget;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

class UsageTargetNode extends Node {
  UsageTargetNode(@NotNull UsageTarget target) {
    setUserObject(target);
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

  @Override
  protected @NotNull String getNodeText() {
    return ObjectUtils.notNull(getTarget().getPresentation().getPresentableText(), "");
  }

  public @NotNull UsageTarget getTarget() {
    return (UsageTarget)getUserObject();
  }

  @Override
  protected void updateNotify() {
    super.updateNotify();
    getTarget().update();
  }
}
