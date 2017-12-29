// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ext.logback

import com.intellij.openapi.util.Key
import com.intellij.psi.*
import com.intellij.psi.CommonClassNames.JAVA_LANG_STRING
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.PsiTreeUtil
import groovy.lang.Closure.DELEGATE_FIRST
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrMethodWrapper
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DELEGATES_TO_KEY
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DELEGATES_TO_STRATEGY_KEY
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.getContainingCall
import org.jetbrains.plugins.groovy.lang.resolve.wrapClassType

class LogbackDelegateMemberContributor : NonCodeMembersContributor() {

  override fun getParentClassName() = componentDelegateFqn

  override fun processDynamicElements(qualifierType: PsiType, processor: PsiScopeProcessor, place: PsiElement, state: ResolveState) {
    val name = processor.getHint(NameHint.KEY)?.getName(state)
    val componentClass = getComponentClass(place) ?: return
    val componentProcessor = ComponentProcessor(processor, place, name)
    if (name == null) {
      componentClass.processDeclarations(componentProcessor, state, null, place)
    }
    else {
      for (prefix in arrayOf("add", "set")) {
        for (method in componentClass.findMethodsByName(prefix + name.capitalize(), true)) {
          if (!componentProcessor.execute(method, state)) return
        }
      }
    }
  }

  fun getComponentClass(place: PsiElement): PsiClass? {
    val reference = place as? GrReferenceExpression ?: return null
    if (reference.isQualified) return null

    val closure = PsiTreeUtil.getParentOfType(reference, GrClosableBlock::class.java) ?: return null
    val call = getContainingCall(closure) ?: return null

    val arguments = PsiUtil.getAllArguments(call)
    if (arguments.isEmpty()) return null

    val lastIsClosure = (arguments.last().type as? PsiClassType)?.resolve()?.qualifiedName == GROOVY_LANG_CLOSURE
    val componentArgumentIndex = (if (lastIsClosure) arguments.size - 1 else arguments.size) - 1
    val componentArgument = arguments.getOrNull(componentArgumentIndex)
    val componentType = ResolveUtil.unwrapClassType(componentArgument?.type) as? PsiClassType
    return componentType?.resolve()
  }

  class ComponentProcessor(val delegate: PsiScopeProcessor, val place: PsiElement, val name: String?) : PsiScopeProcessor {

    override fun execute(method: PsiElement, state: ResolveState): Boolean {
      method as? PsiMethod ?: return true

      val prefix = if (GroovyPropertyUtils.isSetterLike(method, "set")) {
        if (!delegate.execute(method, state)) return false
        "set"
      }
      else if (GroovyPropertyUtils.isSetterLike(method, "add")) {
        val newName = method.name.replaceFirst("add", "set")
        val wrapper = GrMethodWrapper.wrap(method, newName)
        if (!delegate.execute(wrapper, state)) return false
        "add"
      }
      else {
        return true
      }
      val propertyName = method.name.removePrefix(prefix).decapitalize()
      if (name != null && name != propertyName) return true

      val parameter = method.parameterList.parameters.singleOrNull() ?: return true
      val classType = wrapClassType(parameter.type, place) ?: return true
      val wrappedBase = GrLightMethodBuilder(place.manager, propertyName).apply {
        returnType = PsiType.VOID
        navigationElement = method
      }

      // (name, clazz)
      // (name, clazz, configuration)
      wrappedBase.copy().apply {
        addParameter("name", JAVA_LANG_STRING)
        addParameter("clazz", classType)
        addParameter("configuration", GROOVY_LANG_CLOSURE, true)
      }.let {
        if (!delegate.execute(it, state)) return false
      }

      // (clazz)
      // (clazz, configuration)
      wrappedBase.copy().apply {
        addParameter("clazz", classType)
        addAndGetParameter("configuration", GROOVY_LANG_CLOSURE, true).apply {
          putUserData(DELEGATES_TO_KEY, componentDelegateFqn)
          putUserData(DELEGATES_TO_STRATEGY_KEY, DELEGATE_FIRST)
        }
      }.let {
        if (!delegate.execute(it, state)) return false
      }

      return true
    }

    override fun <T : Any?> getHint(hintKey: Key<T>) = if (hintKey == ElementClassHint.KEY) {
      @Suppress("UNCHECKED_CAST")
      ElementClassHint { it == ElementClassHint.DeclarationKind.METHOD } as T
    }
    else {
      null
    }
  }
}