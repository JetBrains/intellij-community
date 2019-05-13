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
package org.jetbrains.debugger

import com.intellij.util.Url
import com.intellij.util.Urls
import com.intellij.util.containers.ContainerUtil
import java.util.concurrent.ConcurrentMap

abstract class ScriptManagerBaseEx<SCRIPT : ScriptBase> : ScriptManagerBase<SCRIPT>() {
  protected val idToScript: ConcurrentMap<String, SCRIPT> = ContainerUtil.newConcurrentMap<String, SCRIPT>()

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