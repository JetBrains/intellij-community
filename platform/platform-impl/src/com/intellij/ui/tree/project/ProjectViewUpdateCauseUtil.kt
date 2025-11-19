// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.tree.project

import com.intellij.ide.plugins.cl.PluginAwareClassLoader
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.ui.treeStructure.ProjectViewUpdateCause
import java.util.*
import kotlin.streams.asSequence

private object LocalPackageNameAccessor

internal fun guessProjectViewUpdateCauseByCaller(calleeClass: Class<*>): ProjectViewUpdateCause {
  val thisPackage = LocalPackageNameAccessor::class.java.packageName
  val calleePackage = calleeClass.packageName
  val walker = StackWalker.getInstance(EnumSet.of(StackWalker.Option.RETAIN_CLASS_REFERENCE))
  val callee = walker.walk { frames ->
    frames.asSequence()
      .firstOrNull { it.declaringClass.packageName == calleePackage }
  }
  if (callee == null) {
    LOG.warn(Throwable("The callee $calleeClass is not present in the call stack, can't detect the caller"))
    return ProjectViewUpdateCause.UNKNOWN
  }
  val caller = walker.walk { frames ->
    frames.asSequence()
      .dropWhile { it.declaringClass.packageName == thisPackage || it.declaringClass.packageName == calleePackage }
      .map { it.declaringClass }
      .firstOrNull()
  }
  if (caller == null) return ProjectViewUpdateCause.UNKNOWN // shouldn't be reasonably possible
  val callerClassloader = caller.getClassLoader()
  if (callerClassloader is PluginAwareClassLoader) return ProjectViewUpdateCause.plugin(callerClassloader.pluginId)
  if (!ApplicationManager.getApplication().isUnitTestMode) {
    LOG.warn(Throwable("${callee.className}.${callee.methodName} called without specifying the cause from $caller"))
  }
  return ProjectViewUpdateCause.UNKNOWN
}

private val LOG = fileLogger()
