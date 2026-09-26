// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.idea.devkit.internal

import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.GeneratedSourcesFilter
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifier

internal class KotlinTestGeneratedSourcesFilter: GeneratedSourcesFilter() {
  override fun isGeneratedSource(file: VirtualFile, project: Project): Boolean {
    val name = file.name
    return if (name.endsWith("TestGenerated.java")) {
      ReadAction.compute<Boolean, RuntimeException> {
        (PsiManager.getInstance(project).findFile(file) as? PsiJavaFile)?.let { psiFile ->
          for (aClass in psiFile.classes) {
            if (aClass.isValid && aClass.hasModifierProperty(PsiModifier.PUBLIC)) {
              return@compute aClass.docComment?.text?.let {
                it.contains("This class is generated") && it.contains("DO NOT MODIFY MANUALLY.")
              } ?: false
            }
          }
        }
        false
      }
    } else {
      false
    }
  }

}