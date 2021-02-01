// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.buildsystem.model


import com.intellij.buildsystem.model.unified.UnifiedCoordinates
import com.intellij.buildsystem.model.unified.UnifiedDependency
import com.intellij.psi.PsiElement

data class DeclaredDependency(
  val unifiedDependency: UnifiedDependency,
  val psiElement: PsiElement?
) {

  val coordinates: UnifiedCoordinates = unifiedDependency.coordinates

  constructor(groupId: String?,
              artifactId: String?,
              version: String?,
              configuration: String? = null,
              psiElement: PsiElement?) :
    this(UnifiedDependency(groupId, artifactId, version, configuration), psiElement)
}