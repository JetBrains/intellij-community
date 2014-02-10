package com.jetbrains.javascript.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface ScriptManager {
  @NotNull
  AsyncResult<String> getSource(@NotNull Script script);

  boolean hasSource(Script script);

  boolean containsScript(Script script);

  /**
   * Demands that script text should be replaced with a new one if possible. VM may get resumed
   * after this command (this is defined by {@link ChangeDescription#isStackModified()}). In this
   * case a standard 'suspended' notification is expected in a moment.
   *
   * @param newSource new text of script
   */
  AsyncResult<ScriptLiveChangeResult> setSourceOnRemote(Script script, String newSource, boolean preview);

  void forEachScript(@NotNull Processor<Script> scriptProcessor);

  @Nullable
  Script forEachScript(@NotNull CommonProcessors.FindProcessor<Script> scriptProcessor);

  @Nullable
  Script getScript(FunctionValue function);

  @Nullable("if call frame script is native (at least in Google Chrome)")
  Script getScript(CallFrame frame);

  @Nullable
  Script findScriptByUrl(@NotNull String rawUrl);

  @Nullable
  ActionCallback getScriptSourceMapLoadCallback(@NotNull Script script);
}