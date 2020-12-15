package com.intellij.completion.ml.local.util

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import java.nio.file.Path

object LocalModelsUtil {
  fun storagePath(project: Project): Path = PathManager.getIndexRoot().toPath().resolve("completion.ml.local").resolve(project.locationHash)

  fun getMethodName(method: PsiMethod): String? = method.presentation?.presentableText

  fun getClassName(cls: PsiClass?): String? = cls?.qualifiedName
}