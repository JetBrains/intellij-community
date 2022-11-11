// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.ide.presentation.Presentation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.ui.IconManager
import icons.GradleIcons
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyPropertyBase
import javax.swing.Icon

@Presentation(typeName = "Gradle Extension")
class GradleExtensionProperty(name: String, private val type: PsiType?, context: PsiElement) : GroovyPropertyBase(name, context) {

  override fun getPropertyType(): PsiType? = type

  override fun getIcon(flags: Int): Icon {
    return IconManager.getInstance().createLayeredIcon(this, GradleIcons.Gradle, flags)
  }

  override fun toString(): String = "Gradle Extension: $name"
}
