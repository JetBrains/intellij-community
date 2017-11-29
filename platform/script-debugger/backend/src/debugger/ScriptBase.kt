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

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.Url
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.sourcemap.SourceMap

abstract class ScriptBase(override val type: Script.Type,
                          override val url: Url,
                          line: Int,
                          override val column: Int,
                          override val endLine: Int) : UserDataHolderBase(), Script {
  override val line = Math.max(line, 0)

  @SuppressWarnings("UnusedDeclaration")
  private @Volatile var source: Promise<String>? = null

  override var sourceMap: SourceMap? = null

  override fun toString() = "[url=$url, lineRange=[$line;$endLine]]"

  override val isWorker: Boolean = false
}