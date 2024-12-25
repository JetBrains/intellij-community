// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.diff.impl.patch;

import com.google.common.collect.Ordering;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

public enum ApplyPatchStatus {
  SUCCESS, PARTIAL, ALREADY_APPLIED, SKIP, FAILURE, ABORT;

  static final List<ApplyPatchStatus> ORDERED_TYPES = Arrays.asList(
    SKIP,
    SUCCESS,
    ALREADY_APPLIED,
    PARTIAL,
    FAILURE,
    ABORT
  );

  //ALREADY_APPLY with SUCCESS should be PARTIAL by historical reasons
  static final Set<ApplyPatchStatus> PARTIAL_ADDITIONAL_SET = ContainerUtil.newHashSet(SUCCESS, ALREADY_APPLIED);
  private static final Ordering<ApplyPatchStatus> ORDERING = Ordering.explicit(ORDERED_TYPES).nullsFirst();

  @Contract("null, null -> null; !null, _ -> !null; _, !null -> !null")
  public static @Nullable ApplyPatchStatus and(@Nullable ApplyPatchStatus lhs, @Nullable ApplyPatchStatus rhs) {
    if (lhs == null) return rhs;
    if (rhs == null) return lhs;

    Set<ApplyPatchStatus> statuses = ContainerUtil.newHashSet(lhs, rhs);
    if (PARTIAL_ADDITIONAL_SET.equals(statuses)) return PARTIAL;
    return ORDERING.max(lhs, rhs);
  }
}