package org.jetbrains.debugger

import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.util.Url
import org.jetbrains.concurrency.Promise
import org.jetbrains.debugger.sourcemap.SourceMap

abstract class ScriptBase(override val type: Script.Type,
                          override val url: Url,
                          override val line: Int,
                          override val column: Int,
                          override val endLine: Int) : UserDataHolderBase(), Script {
  @SuppressWarnings("UnusedDeclaration")
  private @Volatile var source: Promise<String>? = null

  override var sourceMap: SourceMap? = null

  override fun toString() = "[url=$url, lineRange=[$line;$endLine]]"
}