// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
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

  /**
   * Indicates that the number of events from group is bigger than a soft threshold
   *
   * Events will be still recorded until final threshold is reached
   */
  String TOO_MANY_EVENTS_ALERT = "validation.too_many_events.alert";

  /**
   * Indicates that too many events were reported from a group or in total
   *
   * If this threshold is reached, events won't be recorded till the end of the hour
   */
  String TOO_MANY_EVENTS = "validation.too_many_events";

  Set<String> SYSTEM_EVENTS = new HashSet<>(
    Arrays.asList(COLLECTOR_REGISTERED, STATE_COLLECTOR_INVOKED, STATE_COLLECTOR_FAILED, TOO_MANY_EVENTS, TOO_MANY_EVENTS_ALERT));
}
