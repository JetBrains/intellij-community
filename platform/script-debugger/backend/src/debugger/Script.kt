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

import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.util.Url
import org.jetbrains.debugger.sourcemap.SourceMap

interface Script : UserDataHolderEx {
  enum class Type {
    /** A native, internal JavaScript VM script  */
    NATIVE,

    /** A script supplied by an extension  */
    EXTENSION,

    /** A normal user script  */
    NORMAL
  }

  val type: Type

  var sourceMap: SourceMap?

  val url: Url

  val functionName: String?
    get() = null

  val line: Int

  val column: Int

  val endLine: Int

  val isWorker: Boolean
}