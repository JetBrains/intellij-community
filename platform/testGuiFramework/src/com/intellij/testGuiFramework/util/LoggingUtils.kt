// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.util

import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.impl.GuiTestCase
import java.text.SimpleDateFormat

inline val <reified T> T.guilog: Logger
  get() = Logger.getInstance(T::class.java.canonicalName)

inline fun <reified T> guilog(t: T? = null): Logger = Logger.getInstance(T::class.java.canonicalName)

inline fun <reified T> T.logInfo(message: String) {
  guilog.info(LogIndent.indent + message)
}

val currentTimeInHumanString get() = SimpleDateFormat("yyyy.MM.dd_HH.mm.ss.SSS").format(System.currentTimeMillis())!!

fun GuiTestCase.logStartTest(testName: String) {
  logInfo("----------------->>> Test `$testName` started")
}

fun GuiTestCase.logEndTest(testName: String) {
  logInfo("<<<----------------- Test `$testName` finished")
}

inline fun <reified T> T.logError(error: String, t: Throwable? = null) {
  guilog.error(LogIndent.indent + error, t)
}

inline fun <reified T> T.logWarning(warning: String, t: Throwable? = null) {
  guilog.warn(LogIndent.indent + warning, t)
}

fun getClassFileNameAndMethod(): String =
  Thread.currentThread().stackTrace[2].let { "${it.fileName}_${it.methodName}" }

inline fun <reified T, R> T.step(text: String, crossinline block: () -> R): R {
  try {
    guilog.info(LogIndent.indent + text)
    LogIndent.depth.set(LogIndent.depth.get().plus(1))
    return block()
  } catch (e: Throwable) {
    guilog.warn("${LogIndent.indent}Failed on step: $text (${getClassFileNameAndMethod()}, ${e.message})")
    throw e
  }
  finally{
    LogIndent.depth.set(LogIndent.depth.get().minus(1))
  }
}

object LogIndent {
  private const val oneIndent = "  "
  val depth = ThreadLocal.withInitial { 0 }!!
  val indent: String
    get() = oneIndent.repeat(LogIndent.depth.get())
}

