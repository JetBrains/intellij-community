package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.AsyncValueLoaderManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.debugger.values.ValueManager;

import java.util.List;

public abstract class DeclarativeScope<VALUE_LOADER extends ValueManager> extends ScopeBase {
  @SuppressWarnings("unchecked")
  private static final AsyncValueLoaderManager<DeclarativeScope, List<? extends Variable>> VARIABLES_LOADER =
    new AsyncValueLoaderManager<DeclarativeScope, List<? extends Variable>>(DeclarativeScope.class) {
      @Override
      public boolean isUpToDate(@NotNull DeclarativeScope host, @NotNull List<? extends Variable> data) {
        return host.valueManager.getCacheStamp() == host.cacheStamp;
      }

      @Override
      public void load(@NotNull DeclarativeScope host, @NotNull AsyncResult<List<? extends Variable>> result) {
        host.loadVariables(result);
      }
    };

  @SuppressWarnings("UnusedDeclaration")
  private volatile AsyncResult<List<? extends Variable>> variables;

  private volatile int cacheStamp = -1;

  protected final VALUE_LOADER valueManager;

  protected DeclarativeScope(@NotNull Type type, @Nullable String description, @NotNull VALUE_LOADER valueManager) {
    super(type, description);

    this.valueManager = valueManager;
  }

  /**
   * You must call {@link #updateCacheStamp()} when data loaded
   */
  protected abstract void loadVariables(@NotNull AsyncResult<List<? extends Variable>> result);

  protected final void updateCacheStamp() {
    cacheStamp = valueManager.getCacheStamp();
  }

  @NotNull
  @Override
  public final AsyncResult<List<? extends Variable>> getVariables() {
    return VARIABLES_LOADER.get(this);
  }

  @NotNull
  @Override
  public ActionCallback clearCaches() {
    cacheStamp = -1;
    VARIABLES_LOADER.reset(this);
    return ActionCallback.DONE;
  }
}