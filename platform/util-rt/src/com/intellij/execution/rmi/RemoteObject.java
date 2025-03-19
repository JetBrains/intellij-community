/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.execution.rmi;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteObject implements Remote, Unreferenced {

  public static final boolean IN_PROCESS = "true".equals(System.getProperty("idea.rmi.server.in.process"));

  private static final int ALLOWED_EXCEPTIONS_RECURSION_DEPTH = 30;

  private final WeakReference<RemoteObject> myWeakRef;
  private RemoteObject myParent;
  private final Map<RemoteObject, Remote> myChildren = new ConcurrentHashMap<>();

  public RemoteObject() {
    myWeakRef = new WeakReference<>(this);
  }

  public WeakReference<RemoteObject> getWeakRef() {
    return myWeakRef;
  }

  @Contract("!null->!null")
  public synchronized <T extends Remote> T export(@Nullable T child) throws RemoteException {
    if (IN_PROCESS) return child;
    if (child == null) return null;
    @SuppressWarnings("unchecked") final T result = (T)UnicastRemoteObject.exportObject(child, 0);
    myChildren.put((RemoteObject)child, result);
    ((RemoteObject)child).myParent = this;
    return result;
  }

  @Contract("!null->!null")
  public <T extends Remote> T export2(@Nullable T child) throws RemoteException {
    return export(child);
  }

  public synchronized void unexportChildren() throws RemoteException {
    if (IN_PROCESS) return;
    final ArrayList<RemoteObject> childrenRefs = new ArrayList<>(myChildren.keySet());
    myChildren.clear();
    for (RemoteObject child : childrenRefs) {
      child.unreferenced();
    }
  }

  public synchronized void unexportChildren(@NotNull Collection<? extends WeakReference<RemoteObject>> children) throws RemoteException {
    if (IN_PROCESS) return;
    if (children.isEmpty()) return;
    final ArrayList<RemoteObject> list = new ArrayList<>(children.size());
    for (WeakReference<? extends RemoteObject> child : children) {
      RemoteObject element = child.get();
      if (element != null) {
        list.add(element);
      }
    }
    Set<RemoteObject> childrenKeys = myChildren.keySet();
    for (RemoteObject child : list) {
      childrenKeys.remove(child);
      child.unreferenced();
    }
  }

  @Override
  public synchronized void unreferenced() {
    if (IN_PROCESS) return;
    if (myParent != null) {
      myParent.myChildren.remove(this);
      myParent = null;
      try {
        unexportChildren();
        UnicastRemoteObject.unexportObject(this, false);
      }
      catch (RemoteException e) {
        //noinspection CallToPrintStackTrace
        e.printStackTrace();
      }
    }
  }

  public final Throwable wrapException(Throwable ex) {
    return createExceptionProcessor().wrapException(ex);
  }

  protected ExceptionProcessor createExceptionProcessor() {
    return new ExceptionProcessor();
  }

  protected Iterable<RemoteObject> getExportedChildren() {
    return myChildren.keySet();
  }

  public static class ForeignException extends RuntimeException {
    private final String myOriginalClassName; //or store hierarchy here

    public static ForeignException create(String message, Class<?> clazz) {
      String name = clazz.getName();
      if (message.startsWith(name)) {
        int o = name.length();
        if (message.startsWith(":", o)) o += 1;
        message = message.substring(o).trim();
      }
      return new ForeignException(message, name);
    }

    public ForeignException(String message, String originalClassName) {
      super(message);
      myOriginalClassName = originalClassName;
    }

    public String getOriginalClassName() {
      return myOriginalClassName;
    }

    @Override
    public String toString() {
      String s = getOriginalClassName();
      String message = getLocalizedMessage();
      return (message != null) ? (s + ": " + message) : s;
    }
  }

  public static class ExceptionProcessor {
    private final Set<Throwable> recursion = new HashSet<>();

    public final Throwable wrapException(Throwable ex) {
      return ex == null || !recursion.add(ex) || recursion.size() >= ALLOWED_EXCEPTIONS_RECURSION_DEPTH ? null : wrapExceptionStep(ex);
    }

    protected Throwable wrapExceptionStep(Throwable ex) {
      boolean foreignException = false;
      Throwable each = ex;
      while (each != null) {
        if (!each.getClass().getName().startsWith("java.") && !isKnownException(each)) {
          foreignException = true;
          break;
        }
        each = each.getCause();
      }

      if (foreignException) {
        ForeignException wrapper = ForeignException.create(ex.toString(), ex.getClass());
        wrapper.initCause(wrapException(ex.getCause()));
        wrapper.setStackTrace(ex.getStackTrace());
        ex = wrapper;
      }
      return ex;
    }

    protected boolean isKnownException(Throwable ex) {
      return false;
    }
  }
}
