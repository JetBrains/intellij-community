/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.progress;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.Semaphore;

import java.util.concurrent.atomic.AtomicReference;

/**
 * @author irengrig
 *         Date: 6/24/11
 *         Time: 7:34 PM
 */
public abstract class BackgroundSynchronousInvisibleComputable<T> {
  protected abstract T runImpl();

  public T compute() {
    final Semaphore semaphore = new Semaphore();
    semaphore.down();
    final AtomicReference<T> reference = new AtomicReference<T>();
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        try {
          reference.set(runImpl());
        }
        finally {
          semaphore.up();
        }
      }
    });
    semaphore.waitFor();
    return reference.get();
  }
}
