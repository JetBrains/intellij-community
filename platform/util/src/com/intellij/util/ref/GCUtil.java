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
package com.intellij.util.ref;

import com.intellij.diagnostic.ThreadDumper;
import org.jetbrains.annotations.TestOnly;

import java.beans.Introspector;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public class GCUtil {
  /**
   * Try to force VM to collect soft references if possible.
   * This method doesn't guarantee to succeed, and should not be used in the production code.
   * In tests, if you can exactly point to objects you want to GC, use {@code GCWatcher.tracking(objects).tryGc()}
   * which is faster and has more chances to succeed.
   * <p></p>
   * Commits / hours of tweaking method code: 10 / 6
   */
  @TestOnly
  public static void tryGcSoftlyReachableObjects() {
    //long started = System.nanoTime();
    ReferenceQueue<Object> q = new ReferenceQueue<>();
    SoftReference<Object> ref = new SoftReference<>(new Object(), q);
    reachabilityFence(ref.get());

    //noinspection CallToSystemGC
    System.gc();

    if (!allocateTonsOfMemory(() -> q.poll() != null)) {
      //noinspection UseOfSystemOutOrSystemErr
      System.out.println("GCUtil.tryGcSoftlyReachableObjects: giving up");
    }

    // using ref is important as to loop to finish with several iterations: long runs of the method (~80 run of PsiModificationTrackerTest)
    // discovered 'ref' being collected and loop iterated 100 times taking a lot of time
    reachabilityFence(ref.get());

    //System.out.println("Done gc'ing refs:" + ((System.nanoTime() - started) / 1000000));
  }

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  static boolean allocateTonsOfMemory(BooleanSupplier until) {
    long freeMemory = Runtime.getRuntime().freeMemory();

    List<SoftReference<?>> list = new ArrayList<>();
    try {
      for (int i = 0; i < 1000 && !until.getAsBoolean(); i++) {
        // full gc is caused by allocation of large enough array below, SoftReference will be cleared after two full gc
        int bytes = Math.min((int)(Runtime.getRuntime().freeMemory() / 10), Integer.MAX_VALUE / 2);
        list.add(new SoftReference<Object>(new byte[bytes]));
      }
    }
    catch (OutOfMemoryError e) {
      int size = list.size();
      list.clear();
      //noinspection CallToPrintStackTrace
      e.printStackTrace();
      System.err.println("Free memory before: " + freeMemory + "; .freeMemory() now: " + Runtime.getRuntime().freeMemory()+"; list.size(): "+size);
      System.err.println(ThreadDumper.dumpThreadsToString());
      throw e;
    } finally {
      // do not leave a chance for our created SoftReference's content to lie around until next full GC's
      for(SoftReference createdReference:list) createdReference.clear();
    }
    return until.getAsBoolean();
  }

  // They promise in http://mail.openjdk.java.net/pipermail/core-libs-dev/2018-February/051312.html that
  // the object reference won't be removed by JIT and GC-ed until this call
  private static void reachabilityFence(@SuppressWarnings("unused") Object o) {}

  /**
   * Using java beans (e.g. Groovy does it) results in all referenced class infos being cached in ThreadGroupContext. A valid fix
   * would be to hold BeanInfo objects on soft references, but that should be done in JDK. So let's clear this cache manually for now, 
   * in clients that are known to create bean infos. 
   */
  public static void clearBeanInfoCache() {
    Introspector.flushCaches();
  }
}
