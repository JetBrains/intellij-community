// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.uast

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.jetbrains.plugins.groovy.lang.psi.GrQualifiedReference
import org.jetbrains.uast.UAnnotation
import org.jetbrains.uast.UElement
import org.jetbrains.uast.UReferenceExpression

class GrUReferenceExpression(
  override val sourcePsi: GrQualifiedReference<*>,
  parentProvider: () -> UElement?
) : UReferenceExpression {

  override val psi: PsiElement = sourcePsi
  override val javaPsi: PsiElement? = null

  override val resolvedName: String? = (resolve() as? PsiNamedElement)?.name

  override val uastParent: UElement? by lazy(parentProvider)

  override val annotations: List<UAnnotation> = emptyList()

  override fun resolve(): PsiElement? = sourcePsi.resolve()

}