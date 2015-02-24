package org.jetbrains.debugger;

import com.intellij.util.CommonProcessors;
import com.intellij.util.Processor;
import com.intellij.util.Url;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.PromiseManager;

public abstract class ScriptManagerBase<SCRIPT extends ScriptBase> implements ScriptManager {
  @SuppressWarnings("unchecked")
  private final PromiseManager<ScriptBase, String> scriptSourceLoader = new PromiseManager<ScriptBase, String>(ScriptBase.class) {
    @NotNull
    @Override
    public Promise<String> load(@NotNull ScriptBase script) {
      //noinspection unchecked
      return loadScriptSource((SCRIPT)script);
    }
  };

  @NotNull
  protected abstract Promise<String> loadScriptSource(@NotNull SCRIPT script);

  @NotNull
  @Override
  public Promise<String> getSource(@NotNull Script script) {
    if (!containsScript(script)) {
      return Promise.reject("No Script");
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
  public Promise<Void> getScriptSourceMapPromise(@NotNull Script script) {
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

  @Nullable
  @Override
  public Script findScriptById(@NotNull String id) {
    return null;
  }
}