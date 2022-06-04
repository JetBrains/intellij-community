// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.usages.impl.rules;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public enum UsageGroupingRulesDefaultRanks {
  BEFORE_ALL(Integer.MIN_VALUE),

  BEFORE_NON_CODE(-1),
  NON_CODE(0),
  AFTER_NONE_CODE(1),

  BEFORE_SCOPE(99),
  SCOPE(100),
  AFTER_SCOPE(101),

  BEFORE_USAGE_TYPE(199),
  USAGE_TYPE(200),
  AFTER_USAGE_TYPE(201),

  BEFORE_MODULE(299),
  MODULE(300),
  AFTER_MODULE(301),

  BEFORE_DIRECTORY_STRUCTURE(399),
  DIRECTORY_STRUCTURE(400),
  AFTER_DIRECTORY_STRUCTURE(401),

  BEFORE_FILE_STRUCTURE(499),
  FILE_STRUCTURE(500),
  AFTER_FILE_STRUCTURE(501),

  AFTER_ALL(Integer.MAX_VALUE);

  private final int myRank;

  UsageGroupingRulesDefaultRanks(int rank) { myRank = rank; }

  @ApiStatus.Internal
  public int getAbsoluteRank() {
    return myRank;
  }
}
