package com.jetbrains.javascript.debugger;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public abstract class ObjectValueBase<VALUE_LOADER extends ValueLoader> extends ValueBase implements ObjectValue {
  public static final AtomicReferenceFieldUpdater<ObjectValueBase, AsyncResult> PROPERTY_DATA_UPDATER = AtomicReferenceFieldUpdater.newUpdater(ObjectValueBase.class, AsyncResult.class, "propertyData");

  @SuppressWarnings("UnusedDeclaration")
  private volatile AsyncResult<ObjectPropertyData> propertyData;

  protected final VALUE_LOADER valueLoader;

  public ObjectValueBase(ValueType type, VALUE_LOADER valueLoader) {
    super(type);

    this.valueLoader = valueLoader;
  }

  @NotNull
  @Override
  public ObjectValue asObject() {
    return this;
  }

  @Override
  public AsyncResult<ObjectPropertyData> getProperties() {
    //noinspection unchecked
    return valueLoader.getPropertiesLoader().get(this);
  }

  @Nullable
  @Override
  public AsyncResult<FunctionValue> asFunction() {
    return null;
  }
}