package org.jetbrains.debugger

import com.intellij.util.CommonProcessors
import com.intellij.util.Processor
import com.intellij.util.Url
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.values.FunctionValue

interface ScriptManager {

  fun getSource(script: Script): Promise<String>

  fun hasSource(script: Script): Boolean

  fun containsScript(script: Script): Boolean

  /**
   * Demands that script text should be replaced with a new one if possible. VM may get resumed after this command
   */
  fun setSourceOnRemote(script: Script, newSource: CharSequence, preview: Boolean): Promise<*>

  fun forEachScript(scriptProcessor: Processor<Script>)

  fun forEachScript(scriptProcessor: CommonProcessors.FindProcessor<Script>): Script?

  fun getScript(function: FunctionValue): Promise<Script>

  fun getScript(frame: CallFrame): Script?

  fun findScriptByUrl(rawUrl: String): Script?

  fun findScriptByUrl(url: Url): Script?

  fun findScriptById(id: String): Script?

  fun getScriptSourceMapPromise(script: Script): Promise<Void>?

  companion object {
    val VM_SCHEME = "vm"
  }
}