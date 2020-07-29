// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.util.MathUtil;
import com.intellij.util.TimeoutUtil;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.math.BigInteger;

/**
 * @author peter
 */
final class CpuTimings {

  private static final Mandelbrot MANDELBROT = new Mandelbrot(765);

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  static long calcStableCpuTiming() {
    long start = System.currentTimeMillis();

    long minTime = Integer.MAX_VALUE;
    long minIteration = -1;

    for (int i = 0;; i++) {
      long time = TimeoutUtil.measureExecutionTime(MANDELBROT::compute);
      if (time < minTime) {
        minTime = time;
        minIteration = i;
      }
      else if (i - minIteration > 100) {
        System.out.println("CPU timing: " + minTime + ", calculated in " + (System.currentTimeMillis() - start) + "ms");
        return minTime;
      }
    }
  }

  private static class Mandelbrot {
    private final static double LIMIT_SQUARED = 4.0;
    private final static int ITERATIONS = 50;

    Mandelbrot(int size) {
      this.size = size;
      fac = 2.0 / size;

      int offset = size % 8;
      shift = offset == 0 ? 0 : (8 - offset);
    }

    final int size;
    final double fac;
    final int shift;

    void compute() {
      int t = 0;
      for (int y = 0; y < size; y++) {
        t += computeRow(y);
      }
      if (t == 0) {
        throw new AssertionError();
      }
    }

    private int computeRow(int y) {
      int count = 0;

      for (int x = 0; x < size; x++) {
        double Zr = 0.0;
        double Zi = 0.0;
        double Cr = (x * fac - 1.5);
        double Ci = (y * fac - 1.0);

        int i = ITERATIONS;
        double ZrN = 0;
        double ZiN = 0;
        do {
          Zi = 2.0 * Zr * Zi + Ci;
          Zr = ZrN - ZiN + Cr;
          ZiN = Zi * Zi;
          ZrN = Zr * Zr;
        } while (!(ZiN + ZrN > LIMIT_SQUARED) && --i > 0);

        if (i == 0) count++;
      }
      return count;
    }
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
      k *= array[MathUtil.nonNegativeAbs(k) % array.length];
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

  public static void main(String[] args) {
    for (int i = 0; i < 20; i++) {
      // each line can be uncommented alone, to check the results of different benchmarks
      //System.out.println(calcCpuTiming(20, CpuTimings::addBigIntegers));
      //System.out.println(calcCpuTiming(20, CpuTimings::mulDivMemAccess));
      //System.out.println(calcCpuTiming(20, CpuTimings::mulDiv));
    }
  }

}
