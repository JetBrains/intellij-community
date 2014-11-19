package org.jetbrains.debugger.values;

import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.PromiseManager;
import org.jetbrains.debugger.Variable;

import java.util.List;

public abstract class ObjectValueBase<VALUE_LOADER extends ValueManager> extends ValueBase implements ObjectValue {
  @SuppressWarnings("unchecked")
  private static final PromiseManager<ObjectValueBase, List<Variable>> PROPERTIES_LOADER =
    new PromiseManager<ObjectValueBase, List<Variable>>(ObjectValueBase.class) {
      @Override
      public boolean isUpToDate(@NotNull ObjectValueBase host, @NotNull List<Variable> data) {
        return host.valueManager.getCacheStamp() == host.cacheStamp;
      }

      @Override
      public Promise<List<Variable>> load(@NotNull ObjectValueBase host, @NotNull Promise<List<Variable>> promise) {
        if (host.valueManager.isObsolete()) {
          return ValueManager.reject();
        }
        else {
          return ((AsyncPromise<List<Variable>>)host.loadProperties());
        }
      }
    };

  @SuppressWarnings("UnusedDeclaration")
  private volatile Promise<List<? extends Variable>> properties;

  private volatile int cacheStamp = -1;

  protected final VALUE_LOADER valueManager;

  public ObjectValueBase(@NotNull ValueType type, @NotNull VALUE_LOADER valueManager) {
    super(type);

    this.valueManager = valueManager;
  }

  @NotNull
  protected abstract Promise<List<Variable>> loadProperties();

  protected final void updateCacheStamp() {
    cacheStamp = valueManager.getCacheStamp();
  }

  @NotNull
  @Override
  public final Promise<List<Variable>> getProperties() {
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

  @NotNull
  @Override
  public ThreeState hasIndexedProperties() {
    return ThreeState.NO;
  }

  @NotNull
  @Override
  public Promise<Void> getIndexedProperties(int from, int to, int bucketThreshold, @NotNull IndexedVariablesConsumer consumer, @Nullable ValueType componentType) {
    return Promise.REJECTED;
  }

  @Override
  public int getCacheStamp() {
    return cacheStamp;
  }

  @Override
  public void clearCaches() {
    cacheStamp = -1;
    PROPERTIES_LOADER.reset(this);
  }
}