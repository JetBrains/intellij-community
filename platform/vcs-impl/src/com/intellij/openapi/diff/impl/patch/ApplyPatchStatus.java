/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.openapi.diff.impl.patch;

import com.google.common.collect.Ordering;
import com.intellij.util.containers.ContainerUtil;
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

  @Nullable
  public static ApplyPatchStatus and(@Nullable ApplyPatchStatus lhs, @Nullable ApplyPatchStatus rhs) {
    Set<ApplyPatchStatus> statuses = ContainerUtil.newHashSet(lhs, rhs);
    if (PARTIAL_ADDITIONAL_SET.equals(statuses)) return PARTIAL;
    return ORDERING.max(lhs, rhs);
  }
}