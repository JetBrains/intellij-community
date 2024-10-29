// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.editor

import com.intellij.ide.navigationToolbar.StructureAwareNavBarModelExtension
import com.intellij.lang.Language
import org.jetbrains.plugins.groovy.GroovyLanguage
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember

internal class GroovyNavBarExtension : StructureAwareNavBarModelExtension() {
  override val language: Language
    get() = GroovyLanguage

  override fun getPresentableText(e: Any?): String? = when (e) {
    is GrMember -> e.name
    else -> null
  }
}