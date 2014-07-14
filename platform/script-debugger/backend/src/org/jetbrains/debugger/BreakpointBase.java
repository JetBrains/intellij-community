package org.jetbrains.debugger;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class BreakpointBase<L> extends Breakpoint {
  protected int line;
  protected final int column;

  protected boolean enabled;
  protected String condition;

  protected final BreakpointTarget target;

  protected final List<L> actualLocations = ContainerUtil.createLockFreeCopyOnWriteList();

  /**
   * Whether the breakpoint data have changed with respect
   * to the JavaScript VM data
   */
  protected volatile boolean dirty;

  protected BreakpointBase(@NotNull BreakpointTarget target, int line, int column, @Nullable String condition, boolean enabled) {
    this.target = target;
    this.line = line;
    this.column = column;
    this.condition = condition;
    this.enabled = enabled;
  }

  @Override
  public boolean isResolved() {
    return !actualLocations.isEmpty();
  }

  @NotNull
  @Override
  public BreakpointTarget getTarget() {
    return target;
  }

  @Override
  public int getLine() {
    return line;
  }

  public int getColumn() {
    return column;
  }

  @Nullable
  @Override
  public String getCondition() {
    return condition;
  }

  @Override
  public void setCondition(@Nullable String condition) {
    if (StringUtil.equals(this.condition, condition)) {
      return;
    }
    this.condition = condition;
    dirty = true;
  }

  @Override
  public boolean isEnabled() {
    return enabled;
  }

  @Override
  public Breakpoint enabled(boolean value) {
    if (value == enabled) {
      return this;
    }
    enabled = value;
    dirty = true;
    return this;
  }

  public List<L> getActualLocations() {
    return actualLocations;
  }

  public void setActualLocations(@Nullable List<L> value) {
    actualLocations.clear();
    if (value != null && !value.isEmpty()) {
      actualLocations.addAll(value);
    }
  }

  public abstract boolean isVmRegistered();

  @Override
  public int hashCode() {
    int result = line;
    result = 31 * result + column;
    result = 31 * result + (enabled ? 1 : 0);
    if (condition != null) {
      result = 31 * result + condition.hashCode();
    }
    result = 31 * result + target.hashCode();
    return result;
  }
}