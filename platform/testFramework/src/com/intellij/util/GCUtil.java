/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.TestOnly;

import java.beans.Introspector;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class GCUtil {
  /**
   * Try to force VM to collect all the garbage along with soft- and weak-references.
   * Method doesn't guarantee to succeed, and should not be used in the production code.
   */
  @TestOnly
  public static void tryForceGC() {
    tryGcSoftlyReachableObjects();
    WeakReference<Object> weakReference = new WeakReference<>(new Object());
    do {
      System.gc();
    }
    while (weakReference.get() != null);
  }

  /**
   * Try to force VM to collect soft references if possible.
   * Method doesn't guarantee to succeed, and should not be used in the production code.
   * Commits / hours optimized method code: 5 / 3
   */
  @TestOnly
  public static void tryGcSoftlyReachableObjects() {
    //long started = System.nanoTime();
    ReferenceQueue<Object> q = new ReferenceQueue<>();
    SoftReference<Object> ref = new SoftReference<>(new Object(), q);
    ArrayList<SoftReference<?>> list = ContainerUtil.newArrayListWithCapacity(100 + useReference(ref));

    System.gc();
    final long freeMemory = Runtime.getRuntime().freeMemory();

    for (int i = 0; i < 100; i++) {
      if (q.poll() != null) {
        break;
      }

      // full gc is caused by allocation of large enough array below, SoftReference will be cleared after two full gc
      int bytes = Math.min((int)(freeMemory * 0.45), Integer.MAX_VALUE / 2);
      list.add(new SoftReference<>(new byte[bytes]));
    }

    // use ref is important as to loop to finish with several iterations: long runs of the method (~80 run of PsiModificationTrackerTest)
    // discovered 'ref' being collected and loop iterated 100 times taking a lot of time
    list.ensureCapacity(list.size() + useReference(ref));

    // do not leave a chance for our created SoftReference's content to lie around until next full GC's
    for(SoftReference createdReference:list) createdReference.clear();
    //System.out.println("Done gc'ing refs:" + ((System.nanoTime() - started) / 1000000));
  }

  private static int useReference(SoftReference<Object> ref) {
    Object o = ref.get();
    return o == null ? 0 : Math.abs(o.hashCode()) % 10;
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
