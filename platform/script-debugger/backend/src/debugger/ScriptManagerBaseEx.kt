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

abstract class ScriptManagerBaseEx<SCRIPT : ScriptBase> : ScriptManagerBase<SCRIPT>() {
  protected val idToScript = ContainerUtil.newConcurrentMap<String, SCRIPT>()

  override final fun forEachScript(scriptProcessor: (Script) -> Boolean) {
    for (script in idToScript.values) {
      if (!scriptProcessor(script)) {
        return
      }
    }
  }

  override final fun findScriptById(id: String) = idToScript[id]

  fun clear(listener: DebugEventListener) {
    idToScript.clear()
    listener.scriptsCleared()
  }

  override final fun findScriptByUrl(rawUrl: String) = findScriptByUrl(rawUrlToOurUrl(rawUrl))

  override final fun findScriptByUrl(url: Url): SCRIPT? {
    for (script in idToScript.values) {
      if (url.equalsIgnoreParameters(script.url)) {
        return script
      }
    }
    return null
  }

  open fun rawUrlToOurUrl(rawUrl: String) = Urls.parseEncoded(rawUrl)!!
}