package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.AsyncValueLoaderManager;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class ValueLoader<OBJECT_VALUE extends ObjectValueBase, DECLARATIVE_SCOPE extends DeclarativeScopeBase> {
  protected final AtomicInteger cacheStampRef = new AtomicInteger();

  @SuppressWarnings("unchecked")
  private final AsyncValueLoaderManager<OBJECT_VALUE, ObjectPropertyData> propertiesLoader = new AsyncValueLoaderManager<OBJECT_VALUE, ObjectPropertyData>(ObjectValueBase.PROPERTY_DATA_UPDATER) {
    @Override
    public boolean checkFreshness(@NotNull OBJECT_VALUE host, @NotNull ObjectPropertyData data) {
      return cacheStampRef.get() == data.getCacheState();
    }

    @Override
    public void load(@NotNull OBJECT_VALUE host, @NotNull AsyncResult<ObjectPropertyData> result) {
      //noinspection unchecked
      loadProperties(host, result);
    }
  };

  @SuppressWarnings("unchecked")
  final AsyncValueLoaderManager<DECLARATIVE_SCOPE, List<? extends Variable>> declarativeScopeVariablesLoader = new AsyncValueLoaderManager<DECLARATIVE_SCOPE, List<? extends Variable>>(DeclarativeScopeBase.VARIABLES_DATA_UPDATER) {
    @Override
    public boolean checkFreshness(@NotNull DECLARATIVE_SCOPE host, @NotNull List<? extends Variable> data) {
      return cacheStampRef.get() == host.cacheStamp;
    }

    @Override
    public void load(@NotNull DECLARATIVE_SCOPE host, @NotNull AsyncResult<List<? extends Variable>> result) {
      //noinspection unchecked
      loadDeclarativeScopeVariables(host, result);
    }
  };

  protected abstract void loadProperties(@NotNull OBJECT_VALUE value, @NotNull AsyncResult<ObjectPropertyData> result);

  protected abstract void loadDeclarativeScopeVariables(@NotNull DECLARATIVE_SCOPE scope, @NotNull AsyncResult<List<? extends Variable>> result);

  protected final void updateCacheStamp(@NotNull DECLARATIVE_SCOPE scope) {
    scope.cacheStamp = getCacheStamp();
  }

  public AsyncValueLoaderManager<OBJECT_VALUE, ObjectPropertyData> getPropertiesLoader() {
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