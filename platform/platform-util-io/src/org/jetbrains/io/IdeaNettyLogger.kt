// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.io

import com.intellij.openapi.diagnostic.Logger
import io.netty.util.internal.logging.AbstractInternalLogger

internal class IdeaNettyLogger : AbstractInternalLogger("netty") {//NON-NLS
  private fun getLogger() = Logger.getInstance("netty")

  override fun isInfoEnabled() = false

  override fun info(msg: String?) {
  }

  override fun info(format: String?, arg: Any?) {
  }

  override fun info(format: String?, argA: Any?, argB: Any?) {
  }

  override fun info(format: String?, vararg arguments: Any?) {
  }

  override fun info(msg: String?, t: Throwable?) {
  }

  override fun isWarnEnabled() = true

  override fun warn(msg: String?) {
    getLogger().warn(msg)
  }

  override fun warn(format: String?, arg: Any?) {
    getLogger().warn("$format $arg")
  }

  override fun warn(format: String?, vararg arguments: Any?) {
    getLogger().warn("$format $arguments")
  }

  override fun warn(format: String?, argA: Any?, argB: Any?) {
    getLogger().warn("$format $argA $argB")
  }

  override fun warn(msg: String?, t: Throwable?) {
    getLogger().warn(msg, t)
  }

  override fun isErrorEnabled() = true

  override fun error(msg: String?) {
    getLogger().error(msg)
  }

  override fun error(format: String?, arg: Any?) {
    getLogger().error("$format $arg")
  }

  override fun error(format: String?, argA: Any?, argB: Any?) {
    getLogger().error("$format $argA $argB")
  }

  override fun error(format: String?, vararg arguments: Any?) {
    getLogger().error("$format $arguments")
  }

  override fun error(msg: String?, t: Throwable?) {
    getLogger().error(msg, t)
  }

  override fun isDebugEnabled() = false

  override fun debug(msg: String?) {
  }

  override fun debug(format: String?, arg: Any?) {
  }

  override fun debug(format: String?, argA: Any?, argB: Any?) {
  }

  override fun debug(format: String?, vararg arguments: Any?) {
  }

  override fun debug(msg: String?, t: Throwable?) {
  }

  override fun isTraceEnabled() = false

  override fun trace(msg: String?) {
  }

  override fun trace(format: String?, arg: Any?) {
  }

  override fun trace(format: String?, argA: Any?, argB: Any?) {
  }

  override fun trace(format: String?, vararg arguments: Any?) {
  }

  override fun trace(msg: String?, t: Throwable?) {
  }
}