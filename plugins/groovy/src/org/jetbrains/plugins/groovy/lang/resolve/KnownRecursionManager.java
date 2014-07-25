/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.util.Computable;
import com.intellij.util.containers.ContainerUtil;

import java.util.Set;

/**
 * Created by Max Medvedev on 10/04/14
 */
public class KnownRecursionManager {
  private final ThreadLocal<ThreadInfo> myThreads = new ThreadLocal<ThreadInfo>() {
    @Override
    protected ThreadInfo initialValue() {
      return new ThreadInfo();
    }
  };

  public <T> T run(Object key, Computable<T> computable, Object... stopAt) {
    try {
      if (!startInference(key, stopAt)) {
        return null;
      }
      return computable.compute();
    }
    finally {
      try {
        finishInference(key);
      }
      catch (Throwable e) {
        //noinspection ThrowFromFinallyBlock
        throw new RuntimeException("exception in finishInference", e);
      }
    }
  }

  private boolean startInference(Object key, Object[] stopAt) {
    ThreadInfo info = myThreads.get();

    for (Object o : stopAt) {
      if (info.myObjects.contains(o)) return false;
    }
    if (!info.myObjects.add(key)) return false;

    return true;
  }

  private void finishInference(Object key) {
    ThreadInfo info = myThreads.get();

    info.myObjects.remove(key);
  }

  public static KnownRecursionManager getInstance() {
    return ServiceManager.getService(KnownRecursionManager.class);
  }

  private static class ThreadInfo {
    private final Set<Object> myObjects = ContainerUtil.newLinkedHashSet();
  }
}
