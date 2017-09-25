/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import org.jetbrains.annotations.NotNull;

/**
 * A reference to some JavaScript text that you can set breakpoints on. The reference may
 * be in form of script name, script id etc.
 * This type is essentially an Algebraic Type with several cases. Additional cases are provided
 * in form of optional extensions.
 *
 * @see ScriptName
 * @see ScriptId
 */
public abstract class BreakpointTarget {
  /**
   * Dispatches call on the actual Target type.
   *
   * @param visitor user-provided {@link Visitor} that may also implement some additional
   *                interfaces (for extended types) that is checked on runtime
   */
  public abstract <R> R accept(Visitor<R> visitor);

  public interface Visitor<R> {
    R visitScriptName(String scriptName);

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

  public abstract String toString();

  /**
   * A target that refers to a script by its name. Breakpoint will be set on every matching script currently loaded in VM.
   */
  public static final class ScriptName extends BreakpointTarget {
    private final String name;

    public ScriptName(@NotNull String name) {
      this.name = name;
    }

    @NotNull
    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public <R> R accept(@NotNull Visitor<R> visitor) {
      return visitor.visitScriptName(name);
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