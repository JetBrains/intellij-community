package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.containers.ConcurrentHashMap;
import com.intellij.util.containers.ConcurrentHashSet;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public abstract class BreakpointManagerBase<T extends BreakpointBase<?>> implements BreakpointManager {
  protected final Set<T> breakpoints = new ConcurrentHashSet<T>();
  protected final ConcurrentHashMap<T, T> breakpointDuplicationByTarget = new ConcurrentHashMap<T, T>(new TObjectHashingStrategy<T>() {
    @Override
    public int computeHashCode(T b) {
      int result = b.getLine();
      result = 31 * result + b.getColumn();
      if (b.getCondition() != null) {
        result = 31 * result + b.getCondition().hashCode();
      }
      result = 31 * result + b.getTarget().hashCode();
      return result;
    }

    @Override
    public boolean equals(T b1, T b2) {
      return b1.getTarget().getClass() == b2.getTarget().getClass() &&
             b1.getTarget().equals(b2.getTarget()) &&
             b1.getLine() == b2.getLine() &&
             b1.getColumn() == b2.getColumn() &&
             StringUtil.equals(b1.getCondition(), b2.getCondition());
    }
  });

  protected final EventDispatcher<BreakpointListener> dispatcher = EventDispatcher.create(BreakpointListener.class);

  protected abstract T createBreakpoint(@NotNull BreakpointTarget target, int line, int column, @Nullable String condition, int ignoreCount, boolean enabled);

  protected abstract AsyncResult<Breakpoint> doSetBreakpoint(BreakpointTarget target, T breakpoint);

  @Override
  public Breakpoint setBreakpoint(@NotNull final BreakpointTarget target, int line, int column, @Nullable String condition, int ignoreCount, boolean enabled) {
    final T breakpoint = createBreakpoint(target, line, column, condition, ignoreCount, enabled);
    T existingBreakpoint = breakpointDuplicationByTarget.putIfAbsent(breakpoint, breakpoint);
    if (existingBreakpoint != null) {
      return existingBreakpoint;
    }

    breakpoints.add(breakpoint);
    if (enabled) {
      doSetBreakpoint(target, breakpoint).doWhenRejected(new Consumer<String>() {
        @Override
        public void consume(@Nullable String errorMessage) {
          dispatcher.getMulticaster().errorOccurred(breakpoint, errorMessage);
        }
      });
    }
    return breakpoint;
  }

  @Override
  public ActionCallback remove(@NotNull Breakpoint breakpoint) {
    @SuppressWarnings("unchecked")
    T b = (T)breakpoint;
    boolean existed = breakpoints.remove(b);
    if (existed) {
      breakpointDuplicationByTarget.remove(b);
    }
    return !existed || !b.isVmRegistered() ? ActionCallback.DONE : doClearBreakpoint(b);
  }

  @NotNull
  @Override
  public ActionCallback removeAll() {
    BreakpointBase[] list = breakpoints.toArray(new BreakpointBase[breakpoints.size()]);
    breakpoints.clear();
    breakpointDuplicationByTarget.clear();
    ActionCallback.Chunk chunk = new ActionCallback.Chunk();
    for (BreakpointBase b : list) {
      if (b.isVmRegistered()) {
        //noinspection unchecked
        chunk.add(doClearBreakpoint((T)b));
      }
    }
    return chunk.create();
  }

  protected abstract ActionCallback doClearBreakpoint(@NotNull T breakpoint);

  @Override
  public void addBreakpointListener(@NotNull BreakpointListener listener) {
    dispatcher.addListener(listener);
  }

  @Override
  public Iterable<T> getBreakpoints() {
    return breakpoints;
  }

  protected final void notifyBreakpointResolvedListener(@NotNull T breakpoint) {
    if (breakpoint.isResolved()) {
      dispatcher.getMulticaster().resolved(breakpoint);
    }
  }
}