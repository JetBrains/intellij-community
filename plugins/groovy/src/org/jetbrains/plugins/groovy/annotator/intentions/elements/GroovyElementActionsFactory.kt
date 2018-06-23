// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator.intentions.elements

import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.jvm.JvmClass
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.CreateConstructorRequest
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.CreateMethodRequest
import com.intellij.lang.jvm.actions.JvmElementActionsFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiModifier

class GroovyElementActionsFactory : JvmElementActionsFactory() {
  override fun createAddFieldActions(targetClass: JvmClass, request: CreateFieldRequest): List<IntentionAction> {
    val groovyClass = targetClass.toGroovyClassOrNull() ?: return emptyList()

    val constantRequested = request.isConstant || javaClass.isInterface || request.modifiers.containsAll(constantModifiers)
    val result = ArrayList<IntentionAction>()
    if (constantRequested || StringUtil.isCapitalized(request.fieldName)) {
      result += CreateFieldAction(groovyClass, request, true)
    }
    if (!constantRequested) {
      result += CreateFieldAction(groovyClass, request, false)
    }

    if (canCreateEnumConstant(groovyClass, request)) {
      result += CreateEnumConstantAction(groovyClass, request)
    }
    return result
  }

  override fun createAddMethodActions(targetClass: JvmClass, request: CreateMethodRequest): List<IntentionAction> {
    val groovyClass = targetClass.toGroovyClassOrNull() ?: return emptyList()

    val requestedModifiers = request.modifiers
    val staticMethodRequested = JvmModifier.STATIC in requestedModifiers

    val result = ArrayList<IntentionAction>()

    if (groovyClass.isInterface) {
      return if (staticMethodRequested) emptyList() else listOf(CreateMethodAction(groovyClass, request, true))
    } else {
        result += CreatePropertyAction(groovyClass, request, true)
        result += CreatePropertyAction(groovyClass, request, false)
    }

    result += CreateMethodAction(groovyClass, request, false)
    if (!staticMethodRequested && groovyClass.hasModifierProperty(PsiModifier.ABSTRACT)) {
      result += CreateMethodAction(groovyClass, request, true)
    }
    return result
  }

  override fun createAddConstructorActions(targetClass: JvmClass, request: CreateConstructorRequest): List<IntentionAction> {
    val groovyClass = targetClass.toGroovyClassOrNull() ?: return emptyList()
    return listOf(CreateConstructorAction(groovyClass, request))
  }
}

