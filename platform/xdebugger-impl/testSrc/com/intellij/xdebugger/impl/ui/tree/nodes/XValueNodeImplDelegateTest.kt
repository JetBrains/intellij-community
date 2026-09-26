// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui.tree.nodes

import com.intellij.testFramework.UsefulTestCase
import java.lang.reflect.Method
import java.lang.reflect.Modifier


/**
 * Verifies that XValueNodeImplDelegate delegates all overridable methods from XValueNodeImpl.
 */
class XValueNodeImplDelegateTest : UsefulTestCase() {

  fun testDelegatesAllOverridableMethods() {
    val base = XValueNodeImpl::class.java
    val delegate = XValueNodeImplDelegate::class.java

    val baseMethods = getAllOverridableMethods(base)

    val delegateMethods = delegate.declaredMethods.map { it.signatureKey() }.toSet()

    val missing = baseMethods.subtract(delegateMethods)

    if (missing.isNotEmpty()) {
      fail("XValueNodeImplDelegate must delegate the following methods from XValueNodeImpl, but does not:\n" +
           missing.joinToString(separator = "\n"))
    }
  }

  private fun getAllOverridableMethods(clazz: Class<*>?): Set<String> {
    val methods = mutableSetOf<String>()
    val finalMethods = mutableSetOf<String>()
    var current = clazz
    while (current != null && current != Object::class.java) {
      for (m in current.getDeclaredMethods()) {
        val signatureKey = m.signatureKey()
        if (signatureKey in finalMethods) continue
        val mod = m.modifiers
        if (Modifier.isFinal(mod)) {
          finalMethods.add(signatureKey)
          continue
        }
        if (!Modifier.isStatic(mod) && !Modifier.isPrivate(mod)) {
          methods.add(signatureKey)
        }
      }
      current = current.getSuperclass()
    }
    return methods
  }

  private fun Method.signatureKey(): String {
    val params = parameterTypes.joinToString(",") { it.name }
    return "$name($params)"
  }
}
