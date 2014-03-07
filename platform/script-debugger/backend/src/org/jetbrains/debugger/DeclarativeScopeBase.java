package org.jetbrains.debugger;

import com.intellij.openapi.util.AsyncResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

public abstract class DeclarativeScopeBase<VALUE_LOADER extends ValueLoader> extends ScopeBase {
  static final AtomicReferenceFieldUpdater VARIABLES_DATA_UPDATER = AtomicReferenceFieldUpdater.newUpdater(DeclarativeScopeBase.class, AsyncResult.class, "variables");

  @SuppressWarnings("UnusedDeclaration")
  private volatile AsyncResult<List<? extends Variable>> variables;

  volatile int cacheStamp = -1;

  protected final VALUE_LOADER valueLoader;

  protected DeclarativeScopeBase(@NotNull Type type, @Nullable String className, @NotNull VALUE_LOADER loader) {
    super(type, className);

    valueLoader = loader;
  }

  @NotNull
  @Override
  public final AsyncResult<List<? extends Variable>> getVariables() {
    //noinspection unchecked
    return valueLoader.declarativeScopeVariablesLoader.get(this);
  }
}