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

public class UpdatedReference<T> {
  private T myT;
  private long myTime;

  public UpdatedReference(T t) {
    myT = t;
    myTime = System.currentTimeMillis();
  }

  public UpdatedReference(T t, long time) {
    myT = t;
    myTime = time;
  }

  public boolean isTimeToUpdate(final long interval) {
    return (System.currentTimeMillis() - myTime) > interval;
  }

  public void updateT(final T t) {
    myT = t;
    myTime = System.currentTimeMillis();
  }

  public T getT() {
    return myT;
  }

  public void updateTs() {
    myTime = System.currentTimeMillis();
  }

  public long getTime() {
    return myTime;
  }
}
