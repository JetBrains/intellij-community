/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.util;

import com.intellij.openapi.Forceable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.reference.SoftReference;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author Eugene Zhuravlev
 *         Date: Aug 24, 2010
 */
public class LowMemoryWatcher {
  
  public static abstract class ForceableAdapter implements Forceable {
    public boolean isDirty() {
      return true;
    }
  }
  
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.util.LowMemoryWatcher");
  
  private static final ReferenceQueue<Object> ourRefQueue = new ReferenceQueue<Object>();
  @SuppressWarnings({"FieldCanBeLocal"}) 
  private static SoftReference<Object> ourRef;
  private static final List<WeakReference<LowMemoryWatcher>> ourInstances = new CopyOnWriteArrayList<WeakReference<LowMemoryWatcher>>();

  private final Forceable myForceable;

  static {
    final Thread thread = new Thread("LowMemoryWatcher") {
      public void run() {
        updateRef();
        final Set<WeakReference<LowMemoryWatcher>> toRemove = new HashSet<WeakReference<LowMemoryWatcher>>();
        
        while (true)  {
          try {
            ourRefQueue.remove();
            updateRef();
            
            for (WeakReference<LowMemoryWatcher> instanceRef : ourInstances) {
              final LowMemoryWatcher watcher = instanceRef.get();
              if (watcher == null) {
                toRemove.add(instanceRef);
              }
              else {
                try {
                  watcher.doCleanup();
                }
                catch (Throwable e) {
                  LOG.info(e);
                }
              }
            }
            
            if (!toRemove.isEmpty()) {
              ourInstances.removeAll(toRemove);
              toRemove.clear();
            }
            
          }
          catch (InterruptedException ignored) {
          }
        }
      }
    };
    thread.setPriority(Thread.NORM_PRIORITY - 1);
    thread.setDaemon(true);
    thread.start();
  }
  
  public static LowMemoryWatcher register(Forceable forceable) {
    return new LowMemoryWatcher(forceable);
  }
  
  private LowMemoryWatcher(Forceable forceable) {
    myForceable = forceable;
    updateRef();
    ourInstances.add(new WeakReference<LowMemoryWatcher>(this));
  }

  public void stop() {
    for (WeakReference<LowMemoryWatcher> ref : ourInstances) {
      if (ref.get() == this) {
        ourInstances.remove(ref);
        break;
      }
    }
  }
  
  private void doCleanup() {
    if (myForceable.isDirty()) {
      myForceable.force();
    }
  }

  private static void updateRef() {
    ourRef = new SoftReference<Object>(new Object(), ourRefQueue);
  }
}
