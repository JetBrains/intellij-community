package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.AsyncValueLoaderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public abstract class ObjectValueBase<VALUE_LOADER extends ValueManager> extends ValueBase implements ObjectValue {
  @SuppressWarnings("unchecked")
  private static final AsyncValueLoaderManager<ObjectValueBase, ObjectPropertyData> PROPERTIES_LOADER =
    new AsyncValueLoaderManager<ObjectValueBase, ObjectPropertyData>(
      ((AtomicReferenceFieldUpdater)AtomicReferenceFieldUpdater.newUpdater(ObjectValueBase.class, AsyncResult.class, "propertyData"))) {
      @Override
      public boolean checkFreshness(@NotNull ObjectValueBase host, @NotNull ObjectPropertyData data) {
        return host.valueManager.getCacheStamp() == data.getCacheState();
      }

      @Override
      public void load(@NotNull ObjectValueBase host, @NotNull AsyncResult<ObjectPropertyData> result) {
        host.loadProperties(result);
      }
    };

  @SuppressWarnings("UnusedDeclaration")
  private volatile AsyncResult<ObjectPropertyData> propertyData;

  protected final VALUE_LOADER valueManager;

  public ObjectValueBase(@NotNull ValueType type, @NotNull VALUE_LOADER valueManager) {
    super(type);

    this.valueManager = valueManager;
  }

  protected abstract void loadProperties(@NotNull AsyncResult<ObjectPropertyData> result);

  @NotNull
  @Override
  public ObjectValue asObject() {
    return this;
  }

  @Override
  public final AsyncResult<ObjectPropertyData> getProperties() {
    return PROPERTIES_LOADER.get(this);
  }

  @Nullable
  @Override
  public AsyncResult<FunctionValue> asFunction() {
    return null;
  }
}