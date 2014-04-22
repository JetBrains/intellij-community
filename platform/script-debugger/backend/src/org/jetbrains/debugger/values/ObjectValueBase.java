package org.jetbrains.debugger.values;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.AsyncValueLoaderManager;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.Variable;

import java.util.List;

public abstract class ObjectValueBase<VALUE_LOADER extends ValueManager> extends ValueBase implements ObjectValue {
  @SuppressWarnings("unchecked")
  private static final AsyncValueLoaderManager<ObjectValueBase, List<Variable>> PROPERTIES_LOADER =
    new AsyncValueLoaderManager<ObjectValueBase, List<Variable>>(ObjectValueBase.class) {
      @Override
      public boolean isUpToDate(@NotNull ObjectValueBase host, @NotNull List<Variable> data) {
        return host.valueManager.getCacheStamp() == host.cacheStamp;
      }

      @Override
      public void load(@NotNull ObjectValueBase host, @NotNull AsyncResult<List<Variable>> result) {
        host.loadProperties(result);
      }
    };

  @SuppressWarnings("UnusedDeclaration")
  private volatile AsyncResult<List<? extends Variable>> properties;

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
  public final AsyncResult<List<Variable>> getProperties() {
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
  public int getCacheStamp() {
    return cacheStamp;
  }

  @Override
  public void clearCaches() {
    cacheStamp = -1;
    PROPERTIES_LOADER.reset(this);
  }
}