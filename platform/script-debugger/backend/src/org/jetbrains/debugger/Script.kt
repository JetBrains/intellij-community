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
}