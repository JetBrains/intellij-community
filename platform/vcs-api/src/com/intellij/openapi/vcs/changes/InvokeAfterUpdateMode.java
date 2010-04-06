/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.changes;

public enum InvokeAfterUpdateMode {
  SILENT(false, true, false, true),
  BACKGROUND_CANCELLABLE(true, false, false, true),
  BACKGROUND_NOT_CANCELLABLE(false, false, false, true),
  SYNCHRONOUS_CANCELLABLE(true, false, true, true),
  SYNCHRONOUS_NOT_CANCELLABLE(false, false, true, true),
  SILENT_CALLBACK_POOLED(false, true, false, false),
  BACKGROUND_NOT_CANCELLABLE_NOT_AWT(false, false, false, false);

  private final boolean myCancellable;
  private final boolean mySilently;
  private final boolean mySynchronous;
  private final boolean myCallbackOnAwt;

  InvokeAfterUpdateMode(final boolean cancellable, final boolean silently, final boolean synchronous, final boolean callbackOnAwt) {
    myCancellable = cancellable;
    mySilently = silently;
    mySynchronous = synchronous;
    myCallbackOnAwt = callbackOnAwt;
  }

  public boolean isCancellable() {
    return myCancellable;
  }

  public boolean isSilently() {
    return mySilently;
  }

  public boolean isSynchronous() {
    return mySynchronous;
  }

  public boolean isCallbackOnAwt() {
    return myCallbackOnAwt;
  }
}
