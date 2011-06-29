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
package com.intellij.util.continuation;

import com.intellij.util.concurrency.Semaphore;

/**
 * @author irengrig
 *         Date: 6/28/11
 *         Time: 3:23 PM
 */
public class SemaphoreContinuationContext implements ContinuationPause {
  private final Semaphore mySemaphore;

  public SemaphoreContinuationContext() {
    mySemaphore = new Semaphore();
  }

  @Override
  public void suspend() {
    mySemaphore.down();
    mySemaphore.waitFor();
  }

  @Override
  public void ping() {
    mySemaphore.up();
  }
}
