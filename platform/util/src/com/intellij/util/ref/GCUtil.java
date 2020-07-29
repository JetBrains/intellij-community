// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ref;

import com.intellij.diagnostic.ThreadDumper;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.TestOnly;

import java.beans.Introspector;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

@SuppressWarnings("CallToSystemGC")
public final class GCUtil {
  /**
   * Try to force VM to collect soft references if possible.
   * This method doesn't guarantee to succeed, and should not be used in the production code.
   * In tests, if you can exactly point to objects you want to GC, use {@code GCWatcher.tracking(objects).tryGc()}
   * which is faster and has more chances to succeed.
   * <p></p>
   * Commits / hours of tweaking method code: 13 / 7
   */
  @TestOnly
  public static void tryGcSoftlyReachableObjects() {
    //long started = System.nanoTime();
    ReferenceQueue<Object> q = new ReferenceQueue<>();
    SoftReference<Object> ref = new SoftReference<>(new Object(), q);
    ObjectUtils.reachabilityFence(ref.get());

    System.gc();

    StringBuilder log = new StringBuilder();
    if (!allocateTonsOfMemory(log, () -> ref.isEnqueued())) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("GCUtil.tryGcSoftlyReachableObjects: giving up. Log:\n" + log);
    }

    // using ref is important as to loop to finish with several iterations: long runs of the method (~80 run of PsiModificationTrackerTest)
    // discovered 'ref' being collected and loop iterated 100 times taking a lot of time
    ObjectUtils.reachabilityFence(ref.get());

    //System.out.println("Done gc'ing refs:" + ((System.nanoTime() - started) / 1000000));
  }

  @SuppressWarnings({"UseOfSystemOutOrSystemErr", "StringConcatenationInsideStringBufferAppend"})
  static boolean allocateTonsOfMemory(StringBuilder log, BooleanSupplier until) {
    long freeMemory = Runtime.getRuntime().freeMemory();
    log.append("Free memory: " + freeMemory + "\n");

    int liveChunks = 0;
    ReferenceQueue<Object> queue = new ReferenceQueue<>();

    List<SoftReference<?>> list = new ArrayList<>();
    try {
      for (int i = 0; i < 1000 && !until.getAsBoolean(); i++) {
        while (queue.poll() != null) {
          liveChunks--;
        }

        // full gc is caused by allocation of large enough array below, SoftReference will be cleared after two full gc
        int bytes = Math.min((int)(Runtime.getRuntime().totalMemory() / 20), Integer.MAX_VALUE / 2);
        log.append("Iteration " + i + ", allocating new byte[" + bytes + "]" +
                   ", live chunks: " + liveChunks +
                   ", free memory: " + Runtime.getRuntime().freeMemory() + "\n");

        list.add(new SoftReference<Object>(new byte[bytes], queue));
        liveChunks++;

        if (i > 0 && i % 100 == 0 && !until.getAsBoolean()) {
          log.append("  Calling System.gc()\n");
          System.gc();
        }
      }
    }
    catch (OutOfMemoryError e) {
      int size = list.size();
      list.clear();
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
      System.err.println("Log: " + log + "freeMemory() now: " + Runtime.getRuntime().freeMemory()+"; list.size(): "+size);
      System.err.println(ThreadDumper.dumpThreadsToString());
      throw e;
    } finally {
      // do not leave a chance for our created SoftReference's content to lie around until next full GC's
      for(SoftReference createdReference:list) createdReference.clear();
    }
    return until.getAsBoolean();
  }

  /**
   * Using java beans (e.g. Groovy does it) results in all referenced class infos being cached in ThreadGroupContext. A valid fix
   * would be to hold BeanInfo objects on soft references, but that should be done in JDK. So let's clear this cache manually for now,
   * in clients that are known to create bean infos.
   */
  public static void clearBeanInfoCache() {
    Introspector.flushCaches();
  }
}
