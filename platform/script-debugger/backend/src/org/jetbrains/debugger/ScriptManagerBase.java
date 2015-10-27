package org.jetbrains.debugger

import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import com.intellij.util.Url
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.PromiseManager
import org.jetbrains.concurrency.rejectedPromise

abstract class ScriptManagerBase<SCRIPT : ScriptBase> : ScriptManager {
  @Suppress("UNCHECKED_CAST")
  @SuppressWarnings("unchecked")
  private val scriptSourceLoader = object : PromiseManager<ScriptBase, String>(ScriptBase::class.java) {
    override fun load(script: ScriptBase) = loadScriptSource(script as SCRIPT)
  }

  protected abstract fun loadScriptSource(script: SCRIPT): Promise<String>

  override fun getSource(script: Script): Promise<String> {
    if (!containsScript(script)) {
      return rejectedPromise("No Script")
    }
    @Suppress("UNCHECKED_CAST")
    return scriptSourceLoader.get(script as SCRIPT)
  }

  override fun hasSource(script: Script): Boolean {
    @Suppress("UNCHECKED_CAST")
    return scriptSourceLoader.has(script as SCRIPT)
  }

  fun setSource(script: SCRIPT, source: String?) {
    scriptSourceLoader.set(script, source)
  }

  override fun forEachScript(scriptProcessor: CommonProcessors.FindProcessor<Script>): Script? {
    forEachScript((scriptProcessor as Processor<Script>))
    return scriptProcessor.getFoundValue()
  }
}

val Url.isSpecial: Boolean
  get() = !isInLocalFileSystem && (scheme == null || scheme == VM_SCHEME || authority == null)