// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import javax.swing.Icon

@Suppress("EqualsOrHashCode")
class GroovyResolveResultLookupElementDecorator(private val typeText: String?, private val tailText: String?, builder: LookupElementBuilder) : LookupElementDecorator<LookupElementBuilder>(builder) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GroovyResolveResultLookupElementDecorator) return false
    return delegate.lookupString == other.delegate.lookupString && typeText == other.typeText && tailText == other.tailText
  }

  fun withIcon(icon: Icon): GroovyResolveResultLookupElementDecorator = GroovyResolveResultLookupElementDecorator(typeText, tailText, delegate.withIcon(icon))
}