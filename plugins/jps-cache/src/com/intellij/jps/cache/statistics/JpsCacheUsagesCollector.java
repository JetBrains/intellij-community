// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jps.cache.statistics;

import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventFields;
import com.intellij.internal.statistic.eventLog.events.EventId1;
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector;

public class JpsCacheUsagesCollector extends CounterUsagesCollector {
  private static final EventLogGroup GROUP = new EventLogGroup("jps.cache", 1);
  public static final EventId1<Long> DOWNLOAD_DURATION_EVENT_ID = GROUP.registerEvent("CachesDownloadDuration", EventFields.Long("download_time"));
  public static final EventId1<Long> DOWNLOAD_SIZE_EVENT_ID = GROUP.registerEvent("CachesDownloadSize", EventFields.Long("download_size"));

  @Override
  public EventLogGroup getGroup() {
    return GROUP;
  }
}
