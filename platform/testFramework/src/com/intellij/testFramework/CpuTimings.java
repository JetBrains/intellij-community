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
package com.intellij.testFramework;

import com.intellij.util.ArrayUtil;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.stream.LongStream;

/**
 * @author peter
 */
public class CpuTimings {
  final long[] rawData;
  long average;
  private final double myStandardDeviation;

  private CpuTimings(long[] rawData) {
    this.rawData = rawData;
    average = ArrayUtil.averageAmongMedians(rawData, 2);
    myStandardDeviation = standardDeviation(rawData);
  }

  private static double standardDeviation(long[] elapsed) {
    //noinspection ConstantConditions
    double average = LongStream.of(elapsed).mapToDouble(value -> (double)value).average().getAsDouble();
    double variance = 0;
    for (long l : elapsed) {
      variance += Math.pow(average - l, 2);
    }
    return Math.sqrt(variance / average);
  }

  @Override
  public String toString() {
    return "CpuTimings{" + average + ", raw=" + Arrays.toString(rawData) + ", sd=" + myStandardDeviation + '}';
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  static CpuTimings calcStableCpuTiming() {
    int maxIterations = UsefulTestCase.IS_UNDER_TEAMCITY ? 100 : 10;
    for (int i = 0;; i++) {
      CpuTimings timings = calcCpuTiming(20, CpuTimings::addBigIntegers);
      if (timings.myStandardDeviation < 1.8) {
        return timings;
      }
      if (i == maxIterations) {
        System.out.println("Cannot calculate timings that are stable enough, giving up");
        return timings;
      }
      if (i > 3) {
        System.out.println(i + ": Unstable timings: " + timings+" (getProcessCpuLoad() = " + getProcessCpuLoad()+"; getSystemCpuLoad() = " + getSystemCpuLoad()+")");
      }
      System.gc();
    }
  }

  private static CpuTimings calcCpuTiming(int iterationCount, Runnable oneIteration)  {
    long[] elapsed = new long[iterationCount];
    for (int i = 0; i < iterationCount; i++) {
      long start = System.currentTimeMillis();
      oneIteration.run();
      elapsed[i] = System.currentTimeMillis() - start;
    }
    return new CpuTimings(elapsed);
  }

  private static void addBigIntegers() {
    BigInteger k = new BigInteger("1");
    for (int i = 0; i < 1000000; i++) {
      k = k.add(new BigInteger("1"));
    }
  }

  private static void mulDiv() {
    long k = 241;
    for (int i = 0; i < 22_222_222; i++) {
      k = i % 10 == 3 ? k * 239 : k % 12342;
    }
    ensureOdd(k);
  }

  private static void ensureOdd(long k) {
    if (k % 2 == 0) {
      throw new AssertionError("Should be an odd value");
    }
  }

  private static void mulDivMemAccess() {
    int[] array = new int[240_000];
    for (int i = 0; i < array.length; i++) {
      array[i] = i * 42 + 1;
    }
    int k = 241;
    for (int i = 0; i < 5_750_000; i++) {
      k *= array[Math.abs(k) % array.length];
    }
    ensureOdd(k);
  }

  public static double getProcessCpuLoad() {
    try {
      MBeanServer mbs    = ManagementFactory.getPlatformMBeanServer();
      ObjectName name    = ObjectName.getInstance("java.lang:type=OperatingSystem");
      AttributeList list = mbs.getAttributes(name, new String[]{ "ProcessCpuLoad" });

      if (list.isEmpty())     return Double.NaN;

      Attribute att = (Attribute)list.get(0);
      Double value  = (Double)att.getValue();

      // usually takes a couple of seconds before we get real values
      if (value == -1.0)      return Double.NaN;
      // returns a percentage value with 1 decimal point precision
      return ((int)(value * 1000) / 10.0);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }
  public static double getSystemCpuLoad() {
    try {
      MBeanServer mbs    = ManagementFactory.getPlatformMBeanServer();
      ObjectName name    = ObjectName.getInstance("java.lang:type=OperatingSystem");
      AttributeList list = mbs.getAttributes(name, new String[]{ "SystemCpuLoad" });

      if (list.isEmpty())     return Double.NaN;

      Attribute att = (Attribute)list.get(0);
      Double value  = (Double)att.getValue();

      // usually takes a couple of seconds before we get real values
      if (value == -1.0)      return Double.NaN;
      // returns a percentage value with 1 decimal point precision
      return ((int)(value * 1000) / 10.0);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  public static void main(String[] args) {
    for (int i = 0; i < 20; i++) {
      // each line can be uncommented alone, to check the results of different benchmarks
      //System.out.println(calcCpuTiming(20, CpuTimings::addBigIntegers));
      //System.out.println(calcCpuTiming(20, CpuTimings::mulDivMemAccess));
      //System.out.println(calcCpuTiming(20, CpuTimings::mulDiv));
    }
  }

}
