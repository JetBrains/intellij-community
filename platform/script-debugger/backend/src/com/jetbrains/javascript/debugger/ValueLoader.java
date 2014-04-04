package com.jetbrains.javascript.debugger;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.AsyncValueLoaderManager;

import java.util.concurrent.atomic.AtomicInteger;

public abstract class ValueLoader<OBJECT_VALUE extends ObjectValueBase> {
  protected final AtomicInteger cacheStampRef = new AtomicInteger();

  protected final AsyncValueLoaderManager<ObjectValueBase, ObjectPropertyData> propertiesLoader = new AsyncValueLoaderManager<ObjectValueBase, ObjectPropertyData>(ObjectValueBase.PROPERTY_DATA_UPDATER) {
    @Override
    public boolean checkFreshness(ObjectValueBase host, ObjectPropertyData data) {
      return cacheStampRef.get() == data.getCacheState();
    }

    @Override
    public void load(ObjectValueBase host, AsyncResult<ObjectPropertyData> result) {
      //noinspection unchecked
      loadProperties(((OBJECT_VALUE)host), result);
    }
  };

  protected abstract void loadProperties(OBJECT_VALUE value, AsyncResult<ObjectPropertyData> result);

  public AsyncValueLoaderManager<ObjectValueBase, ObjectPropertyData> getPropertiesLoader() {
    return propertiesLoader;
  }

  public void clearCaches() {
    cacheStampRef.incrementAndGet();
  }

  public Runnable getClearCachesTask() {
    return new Runnable() {
      @Override
      public void run() {
        clearCaches();
      }
    };
  }

  public int getCacheStamp() {
    return cacheStampRef.get();
  }
}