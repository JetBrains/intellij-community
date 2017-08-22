/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.internal.statistic;

import com.intellij.internal.statistic.beans.GroupDescriptor;
import com.intellij.internal.statistic.beans.UsageDescriptor;
import com.intellij.openapi.extensions.ExtensionPointName;
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
