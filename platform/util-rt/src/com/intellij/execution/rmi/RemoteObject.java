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

import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.rmi.server.Unreferenced;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteObject implements Remote, Unreferenced {
  private final WeakReference<RemoteObject> myWeakRef;
  private RemoteObject myParent;
  private final Map<RemoteObject, Remote> myChildren = new ConcurrentHashMap<RemoteObject, Remote>();

  public RemoteObject() {
    myWeakRef = new WeakReference<RemoteObject>(this);
  }

  public WeakReference<RemoteObject> getWeakRef() {
    return myWeakRef;
  }

  @Contract("!null->!null")
  public synchronized <T extends Remote> T export(@Nullable T child) throws RemoteException {
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
    final ArrayList<RemoteObject> childrenRefs = new ArrayList<RemoteObject>(myChildren.keySet());
    myChildren.clear();
    for (RemoteObject child : childrenRefs) {
      child.unreferenced();
    }
  }

  public synchronized void unexportChildren(@NotNull Collection<WeakReference<RemoteObject>> children) throws RemoteException {
    if (children.isEmpty()) return;
    final ArrayList<RemoteObject> list = new ArrayList<RemoteObject>(children.size());
    for (WeakReference<? extends RemoteObject> child : children) {
      ContainerUtilRt.addIfNotNull(list, child.get());
    }
    myChildren.keySet().removeAll(list);
    for (RemoteObject child : list) {
      child.unreferenced();
    }
  }

  public synchronized void unreferenced() {
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

  public Throwable wrapException(Throwable ex) {
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
      final RuntimeException wrapper = new RuntimeException(ex.toString());
      wrapper.setStackTrace(ex.getStackTrace());
      wrapper.initCause(wrapException(ex.getCause()));
      ex = wrapper;
    }
    return ex;
  }

  protected boolean isKnownException(Throwable ex) {
    return false;
  }
}
