/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.java.decompiler

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger

class IdeaLogger : IFernflowerLogger() {
  private val LOG = Logger.getInstance(IdeaDecompiler::class.java)

  class InternalException(message: String, cause: Throwable) : RuntimeException(message, cause)

  private var myClass: String? = null

  override fun writeMessage(message: String, severity: IFernflowerLogger.Severity) {
    val text = extendMessage(message)
    when (severity) {
      IFernflowerLogger.Severity.ERROR -> LOG.error(text)
      IFernflowerLogger.Severity.WARN -> LOG.warn(text)
      IFernflowerLogger.Severity.INFO -> LOG.info(text)
      else -> LOG.debug(text)
    }
  }

  override fun writeMessage(message: String, t: Throwable) {
    when (t) {
      is InternalException -> throw t
      is ProcessCanceledException -> throw t
      is InterruptedException -> throw ProcessCanceledException(t)
      else -> throw InternalException(extendMessage(message), t)
    }
  }

  private fun extendMessage(message: String) = if (myClass != null) "$message [$myClass]" else message

  override fun startReadingClass(className: String) {
    LOG.debug("decompiling class " + className)
    myClass = className
  }

  override fun endReadingClass() {
    LOG.debug("... class decompiled")
    myClass = null
  }

  override fun startClass(className: String) {
    LOG.debug("processing class " + className)
  }

  override fun endClass() {
    LOG.debug("... class processed")
  }

  override fun startMethod(methodName: String) {
    LOG.debug("processing method " + methodName)
  }

  override fun endMethod() {
    LOG.debug("... method processed")
  }

  override fun startWriteClass(className: String) {
    LOG.debug("writing class " + className)
  }

  override fun endWriteClass() {
    LOG.debug("... class written")
  }
}