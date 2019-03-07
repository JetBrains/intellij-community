// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.light.LightElement
import org.jetbrains.plugins.groovy.GroovyLanguage

abstract class GroovyPropertyBase(
  private val name: String,
  context: PsiElement
) : LightElement(context.manager, GroovyLanguage),
    GroovyProperty {

  override fun getName(): String = name

  override fun toString(): String = "Groovy Property: $name"
}
