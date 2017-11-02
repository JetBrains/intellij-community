// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic;

import com.intellij.ide.plugins.IdeaPluginDescriptor;
import com.intellij.ide.plugins.PluginManager;
import com.intellij.ide.plugins.cl.PluginClassLoader;
import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Set;
import java.util.function.Function;

public abstract class UsagesCollector {

  public static final ExtensionPointName<UsagesCollector> EP_NAME = ExtensionPointName.create("com.intellij.statistics.usagesCollector");

  @NotNull
  public abstract Set<UsageDescriptor> getUsages() throws CollectUsagesException;

  @NotNull
  public abstract GroupDescriptor getGroupId();

  private static Pair<Double, Double> findBucket(double value, double... ranges) {
    if (ranges.length == 0) throw new IllegalArgumentException("Constrains are empty");
    if (value < ranges[0]) return Pair.create(null, ranges[0]);
    for (int i = 1; i < ranges.length; i++) {
      if (ranges[i] <= ranges[i - 1])
        throw new IllegalArgumentException("Constrains are unsorted");

      if (value < ranges[i]) {
        return Pair.create(ranges[i - 1], ranges[i]);
      }
    }

    return Pair.create(ranges[ranges.length - 1], null);
  }

  public static boolean isNotBundledPluginClass(@NotNull Class clazz) {
    ClassLoader loader = clazz.getClassLoader();
    if (loader instanceof PluginClassLoader) {
      PluginId id = ((PluginClassLoader)loader).getPluginId();
      if (id != null) {
        IdeaPluginDescriptor plugin = PluginManager.getPlugin(id);
        if (plugin != null && !plugin.isBundled()) {
          return true;
        }
      }
    }
    return false;
  }

  protected static String findBucket(long value, Function<Long, String> valueConverter, long...ranges) {
    double[] dRanges = new double[ranges.length];
    for (int i = 0; i < dRanges.length; i++) {
      dRanges[i] = ranges[i];
    }
    return findBucket((double)value, (d) -> valueConverter.apply(d.longValue()), dRanges);
  }

  protected static String findBucket(double value, Function<Double, String> valueConverter, double...ranges) {
    for (double range : ranges) {
      if (range == value) {
        return valueConverter.apply(value);
      }
    }

    Pair<Double, Double> bucket = findBucket(value, ranges);
    if (bucket.first == null) return "(*, " + valueConverter.apply(bucket.second) + ")";
    if (bucket.second == null) return "(" + valueConverter.apply(bucket.first) + ", *)";
    return "(" + valueConverter.apply(bucket.first) + ", " + valueConverter.apply(bucket.second) + ")";
  }
}
