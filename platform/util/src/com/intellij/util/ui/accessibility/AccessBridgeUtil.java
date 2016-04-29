/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui.accessibility;

import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

public class AccessBridgeUtil {

  /**
   * Returns {@code true} if the current thread is the access bridge worker thread.
   *
   * <p>Note: This is an implementation detail that should only be relied on to
   * work around known threading issues in the access bridge implementation.</p>
   */
  public static boolean isWorkerThread() {
    return myStatus.get().myIsWorkerThread;
  }

  /**
   * Post a {@link Computable} on the Event Dispatch Thread, wait for its execution, and return
   * its result.
   *
   * <p>Note: This function is similar to {@link UIUtil#invokeAndWaitIfNeeded(Computable)}}
   * except it must be called from the access bridge worker thread only.</p>
   */
  public static <T> T invokeAndWait(@NotNull final Computable<T> computable) {
    assert isWorkerThread();

    return UIUtil.invokeAndWaitIfNeeded(computable);
  }

  private static class Status {
    public boolean myIsWorkerThread;
  }

  private static final ThreadLocal<Status> myStatus = new ThreadLocal<Status>() {
    @Override
    protected Status initialValue() {
      Status result = new Status();
      result.myIsWorkerThread = _isWorkerThread();
      return result;
    }

    private boolean _isWorkerThread() {
      // Detection only works on windows for now
      if (!SystemInfo.isWindows) {
        return false;
      }

      if (EdtInvocationManager.getInstance().isEventDispatchThread()) {
        return false;
      }

      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      for (StackTraceElement e : stack) {
        if (StringUtil.equals(e.getClassName(), "com.sun.java.accessibility.AccessBridge") &&
            StringUtil.equals(e.getMethodName(), "runDLL")) {
          return true;
        }
      }
      return false;
    }
  };
}
