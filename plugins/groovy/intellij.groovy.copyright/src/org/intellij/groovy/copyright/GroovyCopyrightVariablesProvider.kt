// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.intellij.groovy.copyright

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.maddyhome.idea.copyright.pattern.CopyrightVariablesProvider
import com.maddyhome.idea.copyright.pattern.FileInfo
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

class GroovyCopyrightVariablesProvider : CopyrightVariablesProvider() {

  override fun collectVariables(
    context: MutableMap<String?, Any?>,
    project: Project?,
    module: Module?,
    file: PsiFile,
  ) {
    if (file !is GroovyFile) return

    context["file"] = object : FileInfo(file) {

      override fun getQualifiedClassName() = getClazz()?.qualifiedName ?: super.getQualifiedClassName()

      override fun getClassName(): String = getClazz()?.name ?: super.getClassName()

      private fun getClazz() = if (file.isScript) file.scriptClass else file.classes.firstOrNull()
    }
  }
}