// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.utils

import com.intellij.icons.AllIcons
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElement
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightField
import javax.swing.Icon

@ApiStatus.Internal
fun createGrField(
  aClass: PsiClass,
  property: String,
  stringType: PsiClassType,
  navigationElement: PsiElement,
  originInfo: String,
  icon: Icon,
): GrField {
  val field = GrLightField(aClass, property, stringType, navigationElement)
  field.setIcon(AllIcons.FileTypes.Properties)
  field.originInfo = originInfo
  field.setIcon(icon)
  return field
}