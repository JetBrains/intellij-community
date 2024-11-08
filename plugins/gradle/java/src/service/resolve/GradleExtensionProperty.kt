// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.ide.presentation.Presentation
import com.intellij.psi.OriginInfoAwareElement
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.ui.IconManager
import icons.GradleIcons
import org.jetbrains.plugins.groovy.lang.resolve.api.GroovyPropertyBase
import javax.swing.Icon

@Presentation(typeName = "Gradle Extension")
class GradleExtensionProperty(name: String, val parentKey: String, private val type: PsiType?, private val context: PsiElement) : GroovyPropertyBase(name, context), OriginInfoAwareElement {
  init {
    this.navigationElement = context
  }

  override fun getPropertyType(): PsiType? = type

  override fun getIcon(flags: Int): Icon {
    return IconManager.getInstance().createLayeredIcon(this, GradleIcons.Gradle, flags)
  }

  override fun toString(): String = "Gradle Extension: $name"

  override fun getOriginInfo(): String = GRADLE_EXTENSION_PROPERTY

  override fun getContext(): PsiElement {
    return this.context;
  }

  companion object {
    internal const val GRADLE_EXTENSION_PROPERTY: String = "by gradle sync"
  }

  override fun isEquivalentTo(another: PsiElement?): Boolean {
    //We are only equivalent to any other GradleExtensionProperty with the same name, type and context.
    return another is GradleExtensionProperty && another.name == name &&
           another.context.isEquivalentTo(context) &&
           another.type == type
  }
}
