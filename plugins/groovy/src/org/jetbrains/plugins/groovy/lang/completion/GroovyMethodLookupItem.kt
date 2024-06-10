// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.completion

import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.lookup.LookupElementDecorator

class GroovyMethodLookupItem(builder: LookupElementBuilder) : LookupElementDecorator<LookupElementBuilder>(builder) {
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false
    val otherDecorator = other as? GroovyMethodLookupItem ?: return false
    return delegate.lookupString == otherDecorator.delegate.lookupString
  }
}