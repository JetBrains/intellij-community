package org.jetbrains.debugger;

import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;

public abstract class ContextDependentAsyncResultConsumer<T> implements Consumer<T> {
  protected final SuspendContext context;

  public ContextDependentAsyncResultConsumer(@NotNull SuspendContext context) {
    this.context = context;
  }

  @Override
  public final void consume(T result) {
    Vm vm = context.getVm();
    if (vm.isAttached() && !vm.getSuspendContextManager().isContextObsolete(context)) {
      consume(result, vm);
    }
  }

  protected abstract void consume(T result, @NotNull Vm vm);
}