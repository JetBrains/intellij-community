// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator
import javax.swing.Icon

class GroovyResolveResultLookupElementDecorator(private val typeText: String?, private val tailText: String?, builder: LookupElementBuilder) : LookupElementDecorator<LookupElementBuilder>(builder) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is GroovyResolveResultLookupElementDecorator) return false
    return delegate.lookupString == other.delegate.lookupString && typeText == other.typeText && tailText == other.tailText
  }

  fun withIcon(icon: Icon): GroovyResolveResultLookupElementDecorator = GroovyResolveResultLookupElementDecorator(typeText, tailText, delegate.withIcon(icon))

  override fun hashCode(): Int {
    var result = delegate.lookupString.hashCode()
    result = 31 * result + (typeText?.hashCode() ?: 0)
    result = 31 * result + (tailText?.hashCode() ?: 0)
    return result
  }
}