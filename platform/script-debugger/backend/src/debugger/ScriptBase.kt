// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.debugger

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.Url
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.sourcemap.SourceMap
import kotlin.math.max

abstract class ScriptBase(override val type: Script.Type,
                          override val url: Url,
                          line: Int,
                          override val column: Int,
                          override val endLine: Int,
                          override val vm: Vm) : UserDataHolderBase(), Script {
  override val line: Int = max(line, 0)

  @Suppress("unused")
  @Volatile
  private var source: Promise<String>? = null

  override var sourceMap: SourceMap? = null

  override fun toString(): String = "[url=$url, lineRange=[$line;$endLine]]"
}