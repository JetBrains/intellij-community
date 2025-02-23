// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType

open class SimpleGroovyProperty(name: String, private val type: PsiType?, context: PsiElement) : GroovyPropertyBase(name, context) {

  final override fun getPropertyType(): PsiType? = type
}
