// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.codeInsight.javadoc.JavaDocInfoGeneratorFactory
import com.intellij.ide.presentation.Presentation
import com.intellij.openapi.util.Key
import com.intellij.psi.OriginInfoAwareElement
import com.intellij.psi.PsiElement
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import com.intellij.util.lazyPub
import org.jetbrains.plugins.gradle.util.GradleDocumentationBundle
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder
import org.jetbrains.plugins.groovy.lang.resolve.api.LazyTypeProperty
import javax.swing.Icon

@Presentation(typeName = "Gradle Property")
class GradleGroovyProperty(
  name: String,
  typeFqn: String,
  value: String?,
  context: PsiElement
) : LazyTypeProperty(name, typeFqn, context),
    OriginInfoAwareElement {

  override fun getIcon(flags: Int): Icon = IconManager.getInstance().getPlatformIcon(PlatformIcons.Property)

  companion object {
    internal const val EXTENSION_PROPERTY : String = "via ext"
  }

  override fun getOriginInfo(): String = EXTENSION_PROPERTY

  private val doc by lazyPub {
    val result = StringBuilder()
    result.append("<PRE>")
    JavaDocInfoGeneratorFactory.create(context.project, null).generateType(result, propertyType, context, true)
    result.append(" $name")
    val hasInitializer = !value.isNullOrBlank()
    if (hasInitializer) {
      result.append(" = ")
      val longString = value.toString().length > 100
      if (longString) {
        result.append("<blockquote>")
      }
      result.append(value)
      if (longString) {
        result.append("</blockquote>")
      }
    }
    result.append("</PRE>")
    if (hasInitializer) {
      result.append("<br><b>" + GradleDocumentationBundle.message("gradle.documentation.groovy.initial.value.got.during.last.import") + "</b>")
    }
    result.toString()
  }

  override fun <T : Any?> getUserData(key: Key<T>): T? {
    if (key == NonCodeMembersHolder.DOCUMENTATION) {
      @Suppress("UNCHECKED_CAST")
      return doc as T
    }
    return super.getUserData(key)
  }

  override fun toString(): String = GradleDocumentationBundle.message("gradle.documentation.groovy.gradle.property", name)
}
