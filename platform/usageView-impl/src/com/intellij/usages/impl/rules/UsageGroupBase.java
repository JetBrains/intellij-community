// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import com.intellij.openapi.util.Comparing;
import com.intellij.usages.UsageGroup;
import org.jetbrains.annotations.NotNull;

public abstract class UsageGroupBase implements UsageGroup {
  private final int myOrder;

  protected UsageGroupBase(int order) {
    myOrder = order;
  }

  @Override
  public void navigate(boolean focus) {
  }

  @Override
  public boolean canNavigate() {
    return false;
  }

  @Override
  public boolean canNavigateToSource() {
    return false;
  }

  @Override
  public int compareTo(@NotNull UsageGroup o) {
    if (!(o instanceof UsageGroupBase)) {
      return -1;
    }
    int order = Comparing.compare(myOrder, ((UsageGroupBase)o).myOrder);
    if (order != 0) {
      return order;
    }
    return getPresentableGroupText().compareToIgnoreCase(o.getPresentableGroupText());
  }
}
