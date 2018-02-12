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
}

val Url.isSpecial: Boolean
  get() = !isInLocalFileSystem && (scheme == null || scheme == VM_SCHEME || authority == null)