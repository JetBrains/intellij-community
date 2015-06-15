package org.jetbrains.debugger;

import com.intellij.util.Processor;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentMap;

public abstract class ScriptManagerBaseEx<SCRIPT extends ScriptBase> extends ScriptManagerBase<SCRIPT> {
  protected final ConcurrentMap<String, SCRIPT> idToScript = ContainerUtil.newConcurrentMap();

  @Override
  public final void forEachScript(@NotNull Processor<Script> scriptProcessor) {
    for (SCRIPT script : idToScript.values()) {
      if (!scriptProcessor.process(script)) {
        return;
      }
    }
  }

  @Nullable
  @Override
  public SCRIPT findScriptById(@NotNull String id) {
    return idToScript.get(id);
  }

  public void clear(@NotNull DebugEventListener listener) {
    idToScript.clear();
    listener.scriptsCleared();
  }

  @Nullable
  @Override
  public final Script findScriptByUrl(@NotNull String rawUrl) {
    return findScriptByUrl(rawUrlToOurUrl(rawUrl));
  }

  @Nullable
  @Override
  public final Script findScriptByUrl(@NotNull Url url) {
    for (SCRIPT script : idToScript.values()) {
      if (url.equalsIgnoreParameters(script.getUrl())) {
        return script;
      }
    }
    return null;
  }

  @NotNull
  protected Url rawUrlToOurUrl(@NotNull String url) {
    //noinspection ConstantConditions
    return Urls.parseEncoded(url);
  }
}