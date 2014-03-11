package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.AsyncValueLoaderManager;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public abstract class ObjectValueBase<VALUE_LOADER extends ValueManager> extends ValueBase implements ObjectValue {
  @SuppressWarnings("unchecked")
  private static final AsyncValueLoaderManager<ObjectValueBase, List<? extends Variable>> PROPERTIES_LOADER =
    new AsyncValueLoaderManager<ObjectValueBase, List<? extends Variable>>(
      ((AtomicReferenceFieldUpdater)AtomicReferenceFieldUpdater.newUpdater(ObjectValueBase.class, AsyncResult.class, "propertyData"))) {
      @Override
      public boolean checkFreshness(@NotNull ObjectValueBase host, @NotNull List<? extends Variable> data) {
        return host.valueManager.getCacheStamp() == host.cacheStamp;
      }

      @Override
      public void load(@NotNull ObjectValueBase host, @NotNull AsyncResult<List<? extends Variable>> result) {
        host.loadProperties(result);
      }
    };

  @SuppressWarnings("UnusedDeclaration")
  private volatile AsyncResult<List<? extends Variable>> propertyData;

  private volatile int cacheStamp = -1;

  protected final VALUE_LOADER valueManager;

  public ObjectValueBase(@NotNull ValueType type, @NotNull VALUE_LOADER valueManager) {
    super(type);

    this.valueManager = valueManager;
  }

  protected abstract void loadProperties(@NotNull AsyncResult<List<? extends Variable>> result);

  protected final void updateCacheStamp() {
    cacheStamp = valueManager.getCacheStamp();
  }

  @NotNull
  @Override
  public final AsyncResult<List<? extends Variable>> getProperties() {
    return PROPERTIES_LOADER.get(this);
  }

  @Nullable
  @Override
  public String getValueString() {
    return null;
  }

  @NotNull
  @Override
  public ThreeState hasProperties() {
    return ThreeState.UNSURE;
  }

  @Override
  public int getCacheState() {
    return cacheStamp;
  }
}