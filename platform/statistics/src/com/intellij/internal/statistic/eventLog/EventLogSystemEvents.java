// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

public interface EventLogSystemEvents {
  /**
   * System event which indicates that the counter collector is enabled in current IDE build.
   * Used to calculate counter metric baseline.
   */
  String COLLECTOR_REGISTERED = "registered";

  /**
   * System event which indicates that the collector was called.
   * Used to calculate state metric baseline.
   */
  String STATE_COLLECTOR_INVOKED = "invoked";

  /**
   * System event which indicates that the collector was called but failed with an exception.
   */
  String STATE_COLLECTOR_FAILED = "invocation.failed";
}
