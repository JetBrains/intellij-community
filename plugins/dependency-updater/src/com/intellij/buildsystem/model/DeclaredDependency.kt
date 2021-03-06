// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.buildsystem.model


import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiElement

class DeclaredDependency(
  val unifiedDependency: UnifiedDependency,
  val dataContext: DataContext
) {

  val coordinates: UnifiedCoordinates = unifiedDependency.coordinates
  val psiElement: PsiElement?
    get() {
      return dataContext.getData(CommonDataKeys.PSI_ELEMENT)
    }

  constructor(groupId: String?,
              artifactId: String?,
              version: String?,
              configuration: String? = null,
              dataContext: DataContext) :
    this(UnifiedDependency(groupId, artifactId, version, configuration), dataContext)
}