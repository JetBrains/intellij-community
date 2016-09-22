/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.copyright

import com.intellij.openapi.module.Module
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.maddyhome.idea.copyright.pattern.CopyrightVariablesProvider
import com.maddyhome.idea.copyright.pattern.FileInfo
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile

class GroovyCopyrightVariablesProvider : CopyrightVariablesProvider() {

  override fun collectVariables(context: MutableMap<String, Any>, project: Project, module: Module, file: PsiFile) {
    if (file !is GroovyFile) return

    context.put("file", object : FileInfo(file) {

      override fun getQualifiedClassName() = getClazz()?.qualifiedName ?: super.getQualifiedClassName()

      override fun getClassName(): String = getClazz()?.name ?: super.getClassName()

      private fun getClazz() = if (file.isScript) file.scriptClass else file.classes.firstOrNull()

    })
  }
}