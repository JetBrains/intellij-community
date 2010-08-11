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
package com.intellij.util;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.reference.SoftReference;
import com.intellij.util.concurrency.JBLock;
import com.intellij.util.concurrency.JBReentrantReadWriteLock;
import com.intellij.util.concurrency.LockFactory;
import gnu.trove.TLongArrayList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;

import static com.intellij.util.ObjectUtils.*;

/**
 * @author Dmitry Avdeev
 */
public abstract class CachedValueBase<T> {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.CachedValueImpl");

  private final MyTimedReference<T> myData = new MyTimedReference<T>();
  protected final JBLock r;
  protected final JBLock w;

  public CachedValueBase() {
    JBReentrantReadWriteLock rw = LockFactory.createReadWriteLock();
    r = rw.readLock();
    w = rw.writeLock();
  }


  protected Data<T> computeData(T value, Object[] dependencies) {
    if (dependencies == null) {
      return new Data<T>(value, null, null);
    }

    TLongArrayList timeStamps = new TLongArrayList();
    List<Object> deps = new ArrayList<Object>();
    collectDependencies(timeStamps, deps, dependencies);

    return  new Data<T>(value, ArrayUtil.toObjectArray(deps), timeStamps.toNativeArray());
  }

  protected void setValue(final T value, final CachedValueProvider.Result<T> result) {
    myData.setData(computeData(value == null ? (T)NULL : value, getDependencies(result)));
    if (result != null) {
      myData.setIsLocked(result.isLockValue());
    }
    else {
      myData.setIsLocked(false);
    }
  }

  @Nullable
  protected Object[] getDependencies(CachedValueProvider.Result<T> result) {
    return result == null ? null : result.getDependencyItems();
  }

  @Nullable
  protected Object[] getDependenciesPlusValue(CachedValueProvider.Result<T> result) {
    if (result == null) {
      return null;
    }
    else {
      Object[] items = result.getDependencyItems();
      return result.getValue() == null ? items : items == null ? new Object[] {result.getValue()}: ArrayUtil.append(items, result.getValue());
    }
  }

  public void clear() {
    myData.set(null);
  }

  public void setDataLocked(boolean value) {
    myData.setIsLocked(value);
  }

  public boolean hasUpToDateValue() {
    return getUpToDateOrNull() != null;
  }

  @Nullable
  protected T getUpToDateOrNull() {
    final Data<T> data = myData.getData();

    if (data != null) {
      T value = data.myValue;
      if (isUpToDate(data)) {
        return value;
      }
      if (value instanceof Disposable) {
        Disposer.dispose((Disposable)value);
      }
    }
    return null;
  }

  protected boolean isUpToDate(@NotNull Data data) {
    if (data.myTimeStamps == null) return true;

    for (int i = 0; i < data.myDependencies.length; i++) {
      Object dependency = data.myDependencies[i];
      if (dependency == null) continue;
      if (isDependencyOutOfDate(dependency, data.myTimeStamps[i])) return false;
    }

    return true;
  }

  protected boolean isDependencyOutOfDate(Object dependency, long oldTimeStamp) {
    final long timeStamp = getTimeStamp(dependency);
    return timeStamp < 0 || timeStamp != oldTimeStamp;
  }

  protected void collectDependencies(TLongArrayList timeStamps, List<Object> resultingDeps, Object[] dependencies) {
    for (Object dependency : dependencies) {
      if (dependency == null || dependency == NULL) continue;
      if (dependency instanceof Object[]) {
        collectDependencies(timeStamps, resultingDeps, (Object[])dependency);
      }
      else {
        resultingDeps.add(dependency);
        timeStamps.add(getTimeStamp(dependency));
      }
    }
  }

  protected long getTimeStamp(Object dependency) {

    if (dependency instanceof ModificationTracker) {
      return ((ModificationTracker)dependency).getModificationCount();
    }
    else if (dependency instanceof Reference){
      final Object original = ((Reference)dependency).get();
      if(original == null) return -1;
      return getTimeStamp(original);
    }
    else if (dependency instanceof Ref) {
      final Object original = ((Ref)dependency).get();
      if(original == null) return -1;
      return getTimeStamp(original);
    }
    else if (dependency instanceof Document) {
      return ((Document)dependency).getModificationStamp();
    }
    else {
      LOG.error("Wrong dependency type: " + dependency.getClass());
      return -1;
    }
  }

  public T setValue(final CachedValueProvider.Result<T> result) {
    w.lock();

    try {
      T value = result.getValue();
      setValue(value, result);
      return value;
    }
    finally {
      w.unlock();
    }
  }

  public abstract boolean isFromMyProject(Project project);

  protected static class Data<T> implements Disposable {
    private final T myValue;
    private final Object[] myDependencies;
    private final long[] myTimeStamps;

    public Data(final T value, final Object[] dependencies, final long[] timeStamps) {
      myValue = value;
      myDependencies = dependencies;
      myTimeStamps = timeStamps;
    }

    public void dispose() {
      if (myValue instanceof Disposable) {
        Disposer.dispose((Disposable)myValue);
      }
    }
  }

  private static class MyTimedReference<T> extends TimedReference<SoftReference<Data<T>>> {
    private boolean myIsLocked;


    public MyTimedReference() {
      super(null);
    }

    public void setIsLocked(final boolean isLocked) {
      myIsLocked = isLocked;
    }

    protected boolean isLocked() {
      return super.isLocked() || myIsLocked;
    }

    public void setData(final Data<T> data) {
      set(new SoftReference<Data<T>>(data));
    }

    @Nullable
    public Data<T> getData() {
      final SoftReference<Data<T>> ref = get();
      return ref != null ? ref.get() : null;
    }
  }
}
