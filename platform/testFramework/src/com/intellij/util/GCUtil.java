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

import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.TestOnly;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;

public class GCUtil {
  /**
   * Try to force VM to collect all the garbage along with soft- and weak-references.
   * Method doesn't guarantee to succeed, and should not be used in the production code.
   */
  @TestOnly
  public static void tryForceGC() {
    tryGcSoftlyReachableObjects();
    WeakReference<Object> weakReference = new WeakReference<Object>(new Object());
    do {
      System.gc();
    }
    while (weakReference.get() != null);
  }

  /**
   * Try to force VM to collect soft references if possible.
   * Method doesn't guarantee to succeed, and should not be used in the production code.
   */
  @TestOnly
  public static void tryGcSoftlyReachableObjects() {
    tryClearBeanInfoCache();

    ReferenceQueue<Object> q = new ReferenceQueue<Object>();
    SoftReference<Object> ref = new SoftReference<Object>(new Object(), q);
    ArrayList<Object> list = ContainerUtil.newArrayListWithCapacity(100 + useReference(ref));
    for (int i = 0; i < 100; i++) {
      System.gc();
      if (q.poll() != null) {
        break;
      }
      TimeoutUtil.sleep(10);
      long bytes = Math.min(Runtime.getRuntime().freeMemory() / 2, Integer.MAX_VALUE / 2);
      list.add(new SoftReference<byte[]>(new byte[(int)bytes]));

      // use ref is important as to loop to finish with several iterations: long runs of the method (~80 run of PsiModificationTrackerTest)
      // discovered 'ref' being collected and loop iterated 100 times taking a lot of time
      list.ensureCapacity(list.size() + useReference(ref));
    }
  }

  private static int useReference(SoftReference<Object> ref) {
    Object o = ref.get();
    return o == null ? 0 : Math.abs(o.hashCode()) % 10;
  }

  private static final boolean ourHasBeanInfoCache = SystemInfo.isJavaVersionAtLeast("1.7");

  private static void tryClearBeanInfoCache() {
    if (ourHasBeanInfoCache) {
      try {
        Class<?> aClass = Class.forName("java.beans.ThreadGroupContext");
        Method getContextMethod = aClass.getDeclaredMethod("getContext");
        getContextMethod.setAccessible(true);
        Object contextForThreadGroup = getContextMethod.invoke(null);
        Method clearBeanInfoCacheMethod = contextForThreadGroup.getClass().getDeclaredMethod("clearBeanInfoCache");
        clearBeanInfoCacheMethod.setAccessible(true);
        clearBeanInfoCacheMethod.invoke(contextForThreadGroup);
      }
      catch (Throwable ignore) {}
    }
  }
}
