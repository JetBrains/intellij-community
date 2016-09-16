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
  SILENT(true),
  BACKGROUND_CANCELLABLE(true, false),
  BACKGROUND_NOT_CANCELLABLE(false, false),
  SYNCHRONOUS_CANCELLABLE(true, true),
  SYNCHRONOUS_NOT_CANCELLABLE(false, true),
  SILENT_CALLBACK_POOLED(false);

  private final boolean myCancellable;
  private final boolean mySilent;
  private final boolean mySynchronous;
  private final boolean myCallbackOnAwt;

  // Constructor for silent mode options
  InvokeAfterUpdateMode(boolean callbackOnAwt) {
    this(false, true, false, callbackOnAwt);
  }

  // Constructor for interactive mode options
  InvokeAfterUpdateMode(boolean cancellable, boolean synchronous) {
    this(cancellable, false, synchronous, true);
  }

  InvokeAfterUpdateMode(boolean cancellable, boolean silent, boolean synchronous, boolean callbackOnAwt) {
    myCancellable = cancellable;
    mySilent = silent;
    mySynchronous = synchronous;
    myCallbackOnAwt = callbackOnAwt;
  }

  public boolean isCancellable() {
    return myCancellable;
  }

  public boolean isSilent() {
    return mySilent;
  }

  public boolean isSynchronous() {
    return mySynchronous;
  }

  public boolean isCallbackOnAwt() {
    return myCallbackOnAwt;
  }
}
