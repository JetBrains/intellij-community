package org.jetbrains.debugger;

import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.AsyncValueLoaderManager;
import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ScriptManagerBase<SCRIPT extends ScriptBase> implements ScriptManager {
  @SuppressWarnings("unchecked")
  private final AsyncValueLoaderManager<ScriptBase, String> scriptSourceLoader = new AsyncValueLoaderManager<ScriptBase, String>(ScriptBase.class) {
    @Override
    public void load(@NotNull ScriptBase script, @NotNull AsyncResult<String> result) {
      //noinspection unchecked
      loadScriptSource((SCRIPT)script, result);
    }
  };

  protected abstract void loadScriptSource(SCRIPT script, AsyncResult<String> result);

  @NotNull
  @Override
  public AsyncResult<String> getSource(@NotNull Script script) {
    if (!containsScript(script)) {
      return AsyncResult.rejected();
    }
    //noinspection unchecked
    return scriptSourceLoader.get((SCRIPT)script);
  }

  @Override
  public boolean hasSource(Script script) {
    //noinspection unchecked
    return scriptSourceLoader.has((SCRIPT)script);
  }

  public void setSource(@NotNull SCRIPT script, @Nullable String source) {
    scriptSourceLoader.set(script, source);
  }

  @Nullable
  @Override
  public ActionCallback getScriptSourceMapLoadCallback(@NotNull Script script) {
    return null;
  }

  @Override
  public Script forEachScript(@NotNull CommonProcessors.FindProcessor<Script> scriptProcessor) {
    forEachScript(((Processor<Script>)scriptProcessor));
    return scriptProcessor.getFoundValue();
  }

  public static boolean isSpecial(@NotNull Url url) {
    return !url.isInLocalFileSystem() && (url.getScheme() == null || url.getScheme().equals(ScriptManager.VM_SCHEME) || url.getAuthority() == null);
  }
}