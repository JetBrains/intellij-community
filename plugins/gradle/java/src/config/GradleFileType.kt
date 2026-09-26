// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.config

import com.intellij.openapi.components.serviceOrNull
import com.intellij.psi.PsiFile
import com.intellij.util.application
import org.jetbrains.plugins.gradle.groovy.LegacyGroovyService

fun PsiFile?.isGradleFile(): Boolean {
  if (this == null) return false
  val legacyGroovyService = application.serviceOrNull<LegacyGroovyService>() ?: return false
  return legacyGroovyService.isGradleFile(this)
}