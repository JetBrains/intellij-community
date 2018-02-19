// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.JvmElementActionsFactory
import com.intellij.openapi.util.text.StringUtil

class GroovyElementActionsFactory : JvmElementActionsFactory() {
  override fun createAddFieldActions(targetClass: JvmClass, request: CreateFieldRequest): List<IntentionAction> {
    val javaClass = targetClass.toGroovyClassOrNull() ?: return emptyList()

    val constantRequested = request.constant || javaClass.isInterface || request.modifiers.containsAll(constantModifiers)
    val result = ArrayList<IntentionAction>()
    if (constantRequested || StringUtil.isCapitalized(request.fieldName)) {
      result += CreateFieldAction(javaClass, request, true)
    }
    if (!constantRequested) {
      result += CreateFieldAction(javaClass, request, false)
    }
    return result
  }
}

