/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.debugger;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.Consumer;
import com.intellij.util.EventDispatcher;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

public abstract class BreakpointManagerBase<T extends BreakpointBase<?>> implements BreakpointManager {
  protected final Set<T> breakpoints = ContainerUtil.newConcurrentSet();
  protected final ConcurrentMap<T, T> breakpointDuplicationByTarget =
    ContainerUtil.newConcurrentMap(new TObjectHashingStrategy<T>() {
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

  protected abstract Promise<Breakpoint> doSetBreakpoint(@NotNull BreakpointTarget target, @NotNull T breakpoint);

  @NotNull
  @Override
  public Breakpoint setBreakpoint(@NotNull final BreakpointTarget target, int line, int column, @Nullable String condition, int ignoreCount, boolean enabled) {
    final T breakpoint = createBreakpoint(target, line, column, condition, ignoreCount, enabled);
    T existingBreakpoint = breakpointDuplicationByTarget.putIfAbsent(breakpoint, breakpoint);
    if (existingBreakpoint != null) {
      return existingBreakpoint;
    }

    breakpoints.add(breakpoint);
    if (enabled) {
      doSetBreakpoint(target, breakpoint).rejected(new Consumer<Throwable>() {
        @Override
        public void consume(@NotNull Throwable error) {
          String message = error.getMessage();
          dispatcher.getMulticaster().errorOccurred(breakpoint, message == null ? error.toString() : message);
        }
      });
    }
    return breakpoint;
  }

  @NotNull
  @Override
  public Promise<Void> remove(@NotNull Breakpoint breakpoint) {
    @SuppressWarnings("unchecked")
    T b = (T)breakpoint;
    boolean existed = breakpoints.remove(b);
    if (existed) {
      breakpointDuplicationByTarget.remove(b);
    }
    return !existed || !b.isVmRegistered() ? Promise.DONE : doClearBreakpoint(b);
  }

  @NotNull
  @Override
  public Promise<Void> removeAll() {
    BreakpointBase[] list = breakpoints.toArray(new BreakpointBase[breakpoints.size()]);
    breakpoints.clear();
    breakpointDuplicationByTarget.clear();
    List<Promise<?>> promises = new SmartList<Promise<?>>();
    for (BreakpointBase b : list) {
      if (b.isVmRegistered()) {
        //noinspection unchecked
        promises.add(doClearBreakpoint((T)b));
      }
    }
    return Promise.all(promises);
  }

  @NotNull
  protected abstract Promise<Void> doClearBreakpoint(@NotNull T breakpoint);

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

  @Nullable
  @Override
  public FunctionSupport getFunctionSupport() {
    return null;
  }

  @Override
  public boolean hasScriptRegExpSupport() {
    return false;
  }

  @NotNull
  @Override
  public MUTE_MODE getMuteMode() {
    return MUTE_MODE.ONE;
  }

  @NotNull
  @Override
  public Promise<Void> flush(@NotNull Breakpoint breakpoint) {
    //noinspection unchecked
    return ((T)breakpoint).flush(this);
  }

  @NotNull
  @Override
  public Promise<?> enableBreakpoints(boolean enabled) {
    return Promise.reject("Unsupported");
  }
}