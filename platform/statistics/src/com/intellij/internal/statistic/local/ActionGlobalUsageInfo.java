// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.local;

public class ActionGlobalUsageInfo {
  private final long myUsersCount;
  private final double myUsersRatio;
  private final long myUsagesCount;
  private final double myUsagesPerUserRatio;

  public ActionGlobalUsageInfo(long usersCount, long allUsersCount, long usagesCount) {
    myUsersCount = usersCount;
    myUsagesCount = usagesCount;
    myUsersRatio = ((double)usersCount) / allUsersCount;
    myUsagesPerUserRatio = ((double)usagesCount) / usersCount;
  }

  public long getUsersCount() {
    return myUsersCount;
  }

  public long getUsagesCount() {
    return myUsagesCount;
  }

  public double getUsagesPerUserRatio() {
    return myUsagesPerUserRatio;
  }

  public double getUsersRatio() {
    return myUsersRatio;
  }
}
