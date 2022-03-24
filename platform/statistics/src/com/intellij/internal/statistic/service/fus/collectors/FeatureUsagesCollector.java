// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.collectors;

import com.intellij.ide.plugins.cl.PluginAwareClassLoader;
import com.intellij.internal.statistic.eventLog.EventLogGroup;
import com.intellij.internal.statistic.eventLog.events.EventId;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
  @NonNls private static final String GROUP_ID_PATTERN = "([a-zA-Z]*\\.)*[a-zA-Z]*";

  public final boolean isValid() {
    return Pattern.compile(GROUP_ID_PATTERN).matcher(getGroupId()).matches();
  }

  protected static <T extends FeatureUsagesCollector> Set<T> getExtensions(@NotNull UsagesCollectorConsumer invoker, ExtensionPointName<T> ep) {
    if (invoker.getClass().getClassLoader() instanceof PluginAwareClassLoader) {
      return Collections.emptySet();
    }
    return ep.extensions().filter(u -> u.isValid()).collect(Collectors.toSet());
  }

  /**
   * @deprecated Please use {@link FeatureUsagesCollector#getGroup()} instead.
   */
  @NonNls
  @NotNull
  @Deprecated(forRemoval = true)
  public String getGroupId() {
    EventLogGroup group = getGroup();
    if (group == null) {
      throw new IllegalStateException("Please override either getGroupId() or getGroup()");
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
