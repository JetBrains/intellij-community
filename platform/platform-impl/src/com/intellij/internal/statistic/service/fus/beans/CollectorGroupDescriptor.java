// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.beans;

import com.intellij.internal.statistic.service.fus.collectors.FUSUsageContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class CollectorGroupDescriptor {
  private final String groupID;
  private final FUSUsageContext context;

  private CollectorGroupDescriptor(@NotNull String id, @Nullable FUSUsageContext context) {
    groupID =id;
    this.context = context;
  }

  public static CollectorGroupDescriptor create(@NotNull String id) {
    return new CollectorGroupDescriptor(id, null);
  }

  public static CollectorGroupDescriptor create(@NotNull String id, @Nullable FUSUsageContext context) {
    return new CollectorGroupDescriptor(id, context);
  }

  public String getGroupID() {
    return groupID;
  }

  @Nullable
  public FUSUsageContext getContext() {
    return context;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CollectorGroupDescriptor)) return false;
    CollectorGroupDescriptor that = (CollectorGroupDescriptor)o;
    return groupID.equals(that.getGroupID()) && contextsAreEqual(that);
  }

  private boolean contextsAreEqual(CollectorGroupDescriptor that) {
    if(context == null) return that.getContext() == null;
    return context.equals(that.getContext());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getGroupID(), getContext());
  }
}
