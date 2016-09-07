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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ReflectionUtil;
import com.intellij.util.ui.EdtInvocationManager;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.FocusEvent;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class AccessBridgeUtil {
  private static final Logger LOG = Logger.getInstance("#" + AccessBridgeUtil.class.getName());
  private static final String ACCESS_BRIDGE_CLASS_NAME = "com.sun.java.accessibility.AccessBridge";
  private static InstanceMethodEntry ourFocusGainedMethod;


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

  /*
   * Dispatch a "focus gained" event to screen readers via the access bridge (if it is
   * enabled).
   */
  public static void sendFocusGainedEvent(@Nullable Component focusOwner) {
    if (focusOwner == null) {
      return;
    }

    // Note: It is ok to cache the method + instance obtained from
    // the access bridge because the access bridge is unloaded only
    // when the JVM shuts down.
    if (ourFocusGainedMethod == null) {
      ourFocusGainedMethod = createFocusGainedMethod();
    }

    try {
      FocusEvent event = new FocusEvent(focusOwner, FocusEvent.FOCUS_GAINED, false, null);
      ourFocusGainedMethod.invoke(event);
    }
    catch (Throwable e) {
      LOG.warn("Unexpected error invoking focusGained on the AccessBridge class", e);
    }
  }

  private static class InstanceMethodEntry {
    public static final InstanceMethodEntry Empty = new InstanceMethodEntry(null, null);

    private final Object myInstance;
    private final Method myMethod;

    public InstanceMethodEntry(@Nullable Object instance, @Nullable Method method) {
      myInstance = instance;
      myMethod = method;
    }

    public void invoke(@NotNull FocusEvent event) throws InvocationTargetException, IllegalAccessException {
      if (myMethod == null) {
        return;
      }
      myMethod.invoke(myInstance, event);
    }
  }

  @NotNull
  private static InstanceMethodEntry createFocusGainedMethod() {
    Class accessBridgeClass = findAccessBridgeClass();
    if (accessBridgeClass == null) {
      return InstanceMethodEntry.Empty;
    }

    try {
      // Look for the "AccessBridge.theAccessBridge.eventHandler.focusGained(event)" method.
      Field theAccessBridgeField = ReflectionUtil.findField(accessBridgeClass, null, "theAccessBridge");
      Object theAccessBridge = theAccessBridgeField.get(null);
      if (theAccessBridge == null) {
        return InstanceMethodEntry.Empty;
      }
      Field eventHandlerField = ReflectionUtil.findField(accessBridgeClass, null, "eventHandler");
      Object eventHandler = eventHandlerField.get(theAccessBridge);
      Method method = ReflectionUtil.getMethod(eventHandler.getClass(), "focusGained", FocusEvent.class);
      return new InstanceMethodEntry(eventHandler, method);
    }
    catch (Throwable e) {
      LOG.warn("Unexpected error looking for the AccessBridge class", e);
      return InstanceMethodEntry.Empty;
    }
  }

  /**
   * Find the {@link Class} object corresponding to the {@link com.sun.java.accessibility.AccessBridge} class,
   * or {@code null} if the class his not currently loaded.
   */
  @Nullable
  private static Class findAccessBridgeClass() {
    // We go through the parent chain of ClassLoaders until we find the right loader, because
    // the AccessBridge is loaded via the "Java Extensions" class loader by default.
    //
    // Also, we need to invoke (via reflection) the "findLoadedClass" method on each ClassLoader instance
    // to avoid accidentally loading the AccessBridge class if it is not enabled.
    Method findLoadedClass = ReflectionUtil.getDeclaredMethod(ClassLoader.class, "findLoadedClass", String.class);
    for (ClassLoader cl = ClassLoader.getSystemClassLoader(); cl != null; cl = cl.getParent()) {
      try {
        Class cls = (Class)findLoadedClass.invoke(cl, ACCESS_BRIDGE_CLASS_NAME);
        if (cls != null) {
          return cls;
        }
      }
      catch (Throwable e) {
        LOG.warn("Unexpected error looking for the AccessBridge class", e);
      }
    }
    return null;
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
        if (StringUtil.equals(e.getClassName(), ACCESS_BRIDGE_CLASS_NAME) &&
            StringUtil.equals(e.getMethodName(), "runDLL")) {
          return true;
        }
      }
      return false;
    }
  };
}
