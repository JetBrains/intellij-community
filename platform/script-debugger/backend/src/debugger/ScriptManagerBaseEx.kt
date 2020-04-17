// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.debugger

import com.intellij.util.Url
import com.intellij.util.Urls
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

abstract class ScriptManagerBaseEx<SCRIPT : ScriptBase> : ScriptManagerBase<SCRIPT>() {
  protected val idToScript: ConcurrentMap<String, SCRIPT> = ConcurrentHashMap()

  final override fun forEachScript(scriptProcessor: (Script) -> Boolean) {
    for (script in idToScript.values) {
      if (!scriptProcessor(script)) {
        return
      }
    }
  }

  final override fun findScriptById(id: String): SCRIPT? = idToScript[id]

  fun clear(listener: DebugEventListener) {
    idToScript.clear()
    listener.scriptsCleared()
  }

  final override fun findScriptByUrl(rawUrl: String): SCRIPT? = findScriptByUrl(rawUrlToOurUrl(rawUrl))

  final override fun findScriptByUrl(url: Url): SCRIPT? {
    return idToScript.values.find { url == it.url }
           // TODO Searching ignoring parameters may be fragile, because parameters define script e.g. in webpack. Consider dropping it.
           ?: idToScript.values.find { url.equalsIgnoreParameters(it.url) }
  }

  open fun rawUrlToOurUrl(rawUrl: String): Url = Urls.parseEncoded(rawUrl)!!
}