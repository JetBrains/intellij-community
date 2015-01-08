package org.jetbrains.debugger;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.values.ObjectValue;
import org.jetbrains.debugger.values.ValueManager;

import java.util.List;

public abstract class DeclarativeScope<VALUE_MANAGER extends ValueManager> extends ScopeBase {
  protected VariablesHost<VALUE_MANAGER> childrenManager;

  protected DeclarativeScope(@NotNull Type type, @Nullable String description) {
    super(type, description);
  }

  @NotNull
  protected final Promise<List<Variable>> loadScopeObjectProperties(@NotNull ObjectValue value) {
    if (childrenManager.valueManager.isObsolete()) {
      return ValueManager.reject();
    }

    return value.getProperties().done(new Consumer<List<Variable>>() {
      @Override
      public void consume(List<Variable> variables) {
        childrenManager.updateCacheStamp();
      }
    });
  }

  @NotNull
  @Override
  public final VariablesHost getVariablesHost() {
    return childrenManager;
  }
}