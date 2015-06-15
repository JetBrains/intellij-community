package org.jetbrains.debugger;

import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.debugger.values.FunctionValue;

public interface ScriptManager {
  String VM_SCHEME = "vm";

  @NotNull
  Promise<String> getSource(@NotNull Script script);

  boolean hasSource(Script script);

  boolean containsScript(@NotNull Script script);

  /**
   * Demands that script text should be replaced with a new one if possible. VM may get resumed after this command
   */
  @NotNull
  Promise<?> setSourceOnRemote(@NotNull Script script, @NotNull CharSequence newSource, boolean preview);

  void forEachScript(@NotNull Processor<Script> scriptProcessor);

  @Nullable
  Script forEachScript(@NotNull CommonProcessors.FindProcessor<Script> scriptProcessor);

  @NotNull
  Promise<Script> getScript(@NotNull FunctionValue function);

  @Nullable("if call frame script is native (at least in Google Chrome)")
  Script getScript(@NotNull CallFrame frame);

  @Nullable
  Script findScriptByUrl(@NotNull String rawUrl);

  @Nullable
  Script findScriptByUrl(@NotNull Url url);

  @Nullable
  Script findScriptById(@NotNull String id);

  @Nullable
  Promise<Void> getScriptSourceMapPromise(@NotNull Script script);
}