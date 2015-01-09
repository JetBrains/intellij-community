package org.jetbrains.debugger.values;

import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.Variable;
import org.jetbrains.debugger.VariablesHost;

import java.util.List;

public abstract class ObjectValueBase<VALUE_LOADER extends ValueManager> extends ValueBase implements ObjectValue {
  protected VariablesHost<VALUE_LOADER> childrenManager;

  public ObjectValueBase(@NotNull ValueType type) {
    super(type);
  }

  @NotNull
  @Override
  public final Promise<List<Variable>> getProperties() {
    return childrenManager.get();
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

  @NotNull
  @Override
  public VariablesHost getVariablesHost() {
    return childrenManager;
  }
}