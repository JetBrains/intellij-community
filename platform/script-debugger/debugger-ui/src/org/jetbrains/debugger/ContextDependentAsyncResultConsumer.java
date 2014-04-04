package org.jetbrains.debugger;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

public abstract class ContextDependentAsyncResultConsumer<T> implements Consumer<T> {
  private final Vm vm;
  protected final SuspendContext context;

  public ContextDependentAsyncResultConsumer(@NotNull Vm vm, @NotNull SuspendContext context) {
    this.vm = vm;
    this.context = context;
  }

  @Override
  public final void consume(T result) {
    if (vm.isAttached() && !vm.getSuspendContextManager().isContextObsolete(context)) {
      consume(result, vm);
    }
  }

  protected abstract void consume(T result, @NotNull Vm vm);
}