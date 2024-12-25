// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.diagnostic.PluginException;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.openapi.util.text.Strings;
import org.jetbrains.annotations.*;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * <p>Use it to create a collector which records IDE/project state or user/IDE internal actions.</p>
 * <br/>
 * For more information see <i>fus-collectors.md</i>
 *
 * @see ApplicationUsagesCollector
 * @see ProjectUsagesCollector
 * @see CounterUsagesCollector
 */
@ApiStatus.Internal
public abstract class FeatureUsagesCollector {
  private static final @NonNls String GROUP_ID_PATTERN = "([a-zA-Z]*\\.)*[a-zA-Z]*";
  private @Nullable String fileName = null;

  /**
   * Set environment variable FUS_COLLECTOR_FILENAME_ENABLED to true to get collector's file name in a generated scheme.
   * This property is true for StatisticsEventSchemeGeneration build.
   */
  public FeatureUsagesCollector() {
    boolean isCollectorFileNameEnabled = Boolean.parseBoolean(System.getenv("FUS_COLLECTOR_FILENAME_ENABLED"));
    if (isCollectorFileNameEnabled) {
      calculateFileName();
    }
  }

  @TestOnly
  public void forceCalculateFileName() {
    calculateFileName();
  }

  public final boolean isValid() {
    return Pattern.compile(GROUP_ID_PATTERN).matcher(getGroupId()).matches();
  }

  public @Nullable String getFileName() {
    return fileName;
  }

  private void calculateFileName() {
    StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
    Optional<StackTraceElement> collectorStackTraceElement =
      Arrays.stream(stackTraceElements).filter(x -> Strings.areSameInstance(x.getClassName(), this.getClass().getName())).findFirst();
    collectorStackTraceElement.ifPresent(element -> fileName = element.getFileName());
  }

  /**
   * @deprecated Please use {@link FeatureUsagesCollector#getGroup()} instead.
   */
  @Deprecated(forRemoval = true)
  public @NonNls @NotNull String getGroupId() {
    EventLogGroup group = getGroup();
    if (group == null) {
      throw PluginException.createByClass("Please override either getGroupId() or getGroup() in " + getClass().getName(), null, getClass());
    }
    return group.getId();
  }

  /**
   * Increment collector version if any changes in collector logic were implemented.
   * @deprecated Please use {@link FeatureUsagesCollector#getGroup()} instead.
   */
  @Deprecated(forRemoval = true)
  public int getVersion() {
    EventLogGroup group = getGroup();
    if (group != null) {
      return group.getVersion();
    }
    return 1;
  }

  /**
   * @return EventLogGroup with all registered event IDs and fields in the group
   * @see EventLogGroup#registerEvent
   * @see EventId#metric
   */
  public EventLogGroup getGroup() {
    return null;
  }
}
