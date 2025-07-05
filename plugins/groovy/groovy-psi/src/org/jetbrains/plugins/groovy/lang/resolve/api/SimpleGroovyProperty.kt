// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.resolve.api

import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType

open class SimpleGroovyProperty(name: String, private val type: PsiType?, context: PsiElement) : GroovyPropertyBase(name, context) {

  final override fun getPropertyType(): PsiType? = type


  override fun isEquivalentTo(another: PsiElement?): Boolean {
    if (super.isEquivalentTo(another)) return true;

    //Compare the properties:
    if (another is SimpleGroovyProperty) {
      if (name != another.name) {
        return false;
      }

      if (type != another.type) {
        return false;
      }

      if (context != another.context) {
        return false;
      }

      return true;
    }

    return false;
  }
}
