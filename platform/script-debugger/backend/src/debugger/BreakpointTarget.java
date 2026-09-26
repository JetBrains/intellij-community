// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A reference to some JavaScript text that you can set breakpoints on. The reference may
 * be in form of script name, script id etc.
 * This type is essentially an Algebraic Type with several cases. Additional cases are provided
 * in form of optional extensions.
 *
 * @see ScriptName
 * @see ScriptId
 */
@ApiStatus.Internal
public abstract class BreakpointTarget {
  /**
   * Dispatches call on the actual Target type.
   *
   * @param visitor user-provided {@link Visitor} that may also implement some additional
   *                interfaces (for extended types) that is checked on runtime
   */
  public abstract <R> R accept(Visitor<R> visitor);

  public interface Visitor<R> {
    R visitScriptName(String scriptName, @Nullable Script script);

    R visitScript(Script script);

    R visitUnknown(BreakpointTarget target);
  }

  /**
   * A target that refers to a script by its id
   */
  public static final class ScriptId extends BreakpointTarget {
    public final Script script;

    public ScriptId(@NotNull Script script) {
      this.script = script;
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
      return visitor.visitScript(script);
    }

    @Override
    public String toString() {
      return script.toString();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      return script.equals(((ScriptId)o).script);
    }

    @Override
    public int hashCode() {
      return script.hashCode();
    }
  }

  @Override
  public abstract String toString();

  /**
   * A target that refers to a script by its name. Breakpoint will be set on every matching script currently loaded in VM.
   */
  public static final class ScriptName extends BreakpointTarget {
    private final @NotNull String name;
    private final @Nullable Script script;

    public ScriptName(@NotNull String name) {
      this.name = name;
      this.script = null;
    }

    public ScriptName(@NotNull Script script) {
      this.script = script;
      this.name = script.getUrl().toExternalForm();
    }

    public @NotNull String getName() {
      return name;
    }

    public @Nullable Script getScript() {
      return script;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public <R> R accept(@NotNull Visitor<R> visitor) {
      return visitor.visitScriptName(name, script);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }

      return name.equals(((ScriptName)o).name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }
}