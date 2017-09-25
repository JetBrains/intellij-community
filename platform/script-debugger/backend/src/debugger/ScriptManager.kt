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

import com.intellij.util.Processor
import com.intellij.util.Url
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.values.FunctionValue

const val VM_SCHEME = "vm"

interface ScriptManager {
  fun getSource(script: Script): Promise<String>

  fun hasSource(script: Script): Boolean

  fun containsScript(script: Script): Boolean

  fun forEachScript(scriptProcessor: (Script) -> Boolean)

  fun forEachScript(scriptProcessor: Processor<Script>) = forEachScript { scriptProcessor.process(it)}

  fun getScript(function: FunctionValue): Promise<Script>

  fun getScript(frame: CallFrame): Script?

  fun findScriptByUrl(rawUrl: String): Script?

  fun findScriptByUrl(url: Url): Script?

  fun findScriptById(id: String): Script? = null
}