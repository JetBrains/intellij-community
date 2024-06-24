// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.jps.bridge.impl.java

import com.intellij.platform.workspace.jps.bridge.impl.module.JpsDependencyElementBridge
import com.intellij.platform.workspace.jps.bridge.impl.reportModificationAttempt
import com.intellij.platform.workspace.jps.entities.DependencyScope
import org.jetbrains.jps.model.ex.JpsElementBase
import org.jetbrains.jps.model.java.JpsJavaDependencyExtension
import org.jetbrains.jps.model.java.JpsJavaDependencyScope

internal class JpsJavaDependencyExtensionBridge(
  private val exported: Boolean,
  private val scope: DependencyScope,
  parentElement: JpsDependencyElementBridge)
  : JpsElementBase<JpsJavaDependencyExtensionBridge>(), JpsJavaDependencyExtension {
  
  init {
    parent = parentElement
  }

  override fun isExported(): Boolean = exported

  override fun getScope(): JpsJavaDependencyScope = when (scope) {
    DependencyScope.COMPILE -> JpsJavaDependencyScope.COMPILE
    DependencyScope.TEST -> JpsJavaDependencyScope.TEST
    DependencyScope.RUNTIME -> JpsJavaDependencyScope.RUNTIME
    DependencyScope.PROVIDED -> JpsJavaDependencyScope.PROVIDED
  }

  override fun setExported(exported: Boolean) {
    reportModificationAttempt()
  }

  override fun setScope(scope: JpsJavaDependencyScope) {
    reportModificationAttempt()
  }
}
