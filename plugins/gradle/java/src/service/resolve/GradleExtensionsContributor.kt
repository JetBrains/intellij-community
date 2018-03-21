// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.codeInsight.javadoc.JavaDocInfoGenerator
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Ref
import com.intellij.patterns.ElementPattern
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PsiJavaPatterns.psiElement
import com.intellij.psi.CommonClassNames.*
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.ElementClassHint
import com.intellij.psi.scope.NameHint
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.util.ProcessingContext
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings.GradleProp
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings.GradleTask
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder.DOCUMENTATION
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes.COMPOSITE_LSHIFT_SIGN
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.*
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path.GrMethodCallExpressionImpl
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns.groovyBinaryExpression
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DELEGATES_TO_KEY
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DELEGATES_TO_STRATEGY_KEY
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo

/**
 * @author Vladislav.Soroka
 * @since 11/16/2016
 */
class GradleExtensionsContributor : GradleMethodContextContributor {

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    val extensionsData = Companion.getExtensionsFor(closure) ?: return null
    for (extension in extensionsData.extensions) {
      val extensionClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, extension.name))
      if (extensionClosure.accepts(closure)) {
        return DelegatesToInfo(TypesUtil.createType(extension.rootTypeFqn, closure), Closure.DELEGATE_FIRST)
      }
      val objectTypeFqn = extension.namedObjectTypeFqn?.let { if (it.isNotBlank()) it else null } ?: continue
      val objectType = TypesUtil.createType(objectTypeFqn, closure)
      val objectClosure = groovyClosure().withAncestor(2, extensionClosure)
      if (objectClosure.accepts(closure)) {
        return DelegatesToInfo(objectType, Closure.DELEGATE_FIRST)
      }

      val objectReference = object : ElementPattern<PsiElement> {
        override fun getCondition() = null
        override fun accepts(o: Any?) = false
        override fun accepts(o: Any?, context: ProcessingContext): Boolean {
          return o is GrExpression && o.type?.isAssignableFrom(objectType) ?: false
        }
      }
      if (psiElement().withParent(
        psiElement().withFirstChild(objectReference)).accepts(closure)) {
        return DelegatesToInfo(objectType, Closure.DELEGATE_FIRST)
      }
    }
    return null
  }

  override fun process(methodCallInfo: MutableList<String>,
                       processor: PsiScopeProcessor,
                       state: ResolveState,
                       place: PsiElement): Boolean {
    val extensionsData = getExtensionsFor(place) ?: return true
    val classHint = processor.getHint(ElementClassHint.KEY)
    val shouldProcessMethods = ResolveUtil.shouldProcessMethods(classHint)
    val shouldProcessProperties = ResolveUtil.shouldProcessProperties(classHint)
    val groovyPsiManager = GroovyPsiManager.getInstance(place.project)
    val resolveScope = place.resolveScope
    val projectClass = JavaPsiFacade.getInstance(place.project).findClass(GRADLE_API_PROJECT, resolveScope) ?: return true
    val name = processor.getHint(NameHint.KEY)?.getName(state)

    if (psiElement().inside(closureInLeftShiftMethod).accepts(place)) {
      if (!GradleResolverUtil.processDeclarations(processor, state, place, GRADLE_API_DEFAULT_TASK)) {
        return false
      }
    }

    if (place.text == "task" && place is GrReferenceExpression && place.parent is GrApplicationStatement) {
      if (GradleResolverUtil.isLShiftElement(place.parent?.children?.getOrNull(1)?.firstChild)) {
        val taskContainerClass = JavaPsiFacade.getInstance(place.project).findClass(GRADLE_API_TASK_CONTAINER, resolveScope) ?: return true
        val returnClass = groovyPsiManager.createTypeByFQClassName(GRADLE_API_TASK, resolveScope) ?: return true
        val methodBuilder = GrLightMethodBuilder(place.manager, "create").apply {
          containingClass = taskContainerClass
          returnType = returnClass
        }
        methodBuilder.addParameter("task", GRADLE_API_TASK, true)
        place.putUserData(RESOLVED_CODE, true)
        if (!processor.execute(methodBuilder, state)) return false
      }
    }

    for (extension in extensionsData.extensions) {
      if (!processExtension(processor, state, place, extension)) return false
      if (name == extension.name) break
    }

    if (place.getUserData(RESOLVED_CODE).let { it == null || !it }) {
      if (psiElement().withAncestor(2, groovyClosure().with(object : PatternCondition<GrClosableBlock?>("withDelegatesToInfo") {
        override fun accepts(t: GrClosableBlock, context: ProcessingContext?): Boolean {
          return getDelegatesToInfo(t) != null
        }
      })).accepts(place)) {
        return true
      }

      var isTaskDeclaration = false
      val parent = place.parent
      val superParent = parent?.parent
      if (superParent is GrCommandArgumentList && superParent.parent?.firstChild?.text == "task") {
        isTaskDeclaration = true
      }

      for (gradleTask in extensionsData.tasks) {
        if (shouldProcessMethods && name == gradleTask.name) {
          val returnClass = groovyPsiManager.createTypeByFQClassName(
            if (isTaskDeclaration) JAVA_LANG_STRING else gradleTask.typeFqn, resolveScope) ?: continue

          val methodBuilder = GrLightMethodBuilder(place.manager, gradleTask.name).apply {
            containingClass = projectClass
            returnType = returnClass
            if (parent is GrMethodCallExpressionImpl && parent.argumentList.namedArguments.isNotEmpty()) {
              addParameter("args", JAVA_UTIL_MAP, true)
            }
            val closureParam = addAndGetParameter("configuration", GROOVY_LANG_CLOSURE, true)
            closureParam.putUserData(DELEGATES_TO_KEY, gradleTask.typeFqn)
            closureParam.putUserData(DELEGATES_TO_STRATEGY_KEY, Closure.OWNER_FIRST)
          }
          if (!processor.execute(methodBuilder, state)) return false
          break
        }

        val taskClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, gradleTask.name))
        val psiElement = psiElement()
        if (psiElement.inside(taskClosure).accepts(place)) {
          if (shouldProcessMethods && !GradleResolverUtil.processDeclarations(processor, state, place, gradleTask.typeFqn)) {
            place.putUserData(RESOLVED_CODE, null)
            return false
          }
          else {
            place.putUserData(RESOLVED_CODE, null)
          }
        }
      }

      if (name != null && place is GrReferenceExpression && !place.isQualified) {
        if (!shouldProcessMethods && shouldProcessProperties) {
          for (gradleTask in extensionsData.tasks) {
            if (name == gradleTask.name) {
              val docRef = Ref.create<String>()
              val variable = object : GrLightVariable(place.manager, name, gradleTask.typeFqn, place) {
                override fun getNavigationElement(): PsiElement {
                  val navigationElement = super.getNavigationElement()
                  navigationElement.putUserData(DOCUMENTATION, docRef.get())
                  return navigationElement
                }
              }
              val doc = getDocumentation(gradleTask, variable)
              docRef.set(doc)
              place.putUserData(DOCUMENTATION, doc)
              if (!processor.execute(variable, state)) return false
              break
            }
          }
        }

        val propExecutionResult = extensionsData.findProperty(name)?.let {
          if (!shouldProcessMethods && shouldProcessProperties) {
            val docRef = Ref.create<String>()
            val variable = object : GrLightVariable(place.manager, name, it.typeFqn, place) {
              override fun getNavigationElement(): PsiElement {
                val navigationElement = super.getNavigationElement()
                navigationElement.putUserData(DOCUMENTATION, docRef.get())
                return navigationElement
              }
            }
            val doc = getDocumentation(it, variable)
            docRef.set(doc)
            place.putUserData(DOCUMENTATION, doc)
            return processor.execute(variable, state)
          }
          else if (shouldProcessMethods && it.typeFqn == GROOVY_LANG_CLOSURE) {
            val returnClass = groovyPsiManager.createTypeByFQClassName(GROOVY_LANG_CLOSURE, resolveScope) ?: return true
            val methodBuilder = GrLightMethodBuilder(place.manager, name).apply {
              returnType = returnClass
              addParameter("args", JAVA_LANG_OBJECT, true)
            }
            return processor.execute(methodBuilder, state)
          }
          true
        }
        if (propExecutionResult != null && propExecutionResult) return false
      }
    }

    return true
  }

  companion object {
    val closureInLeftShiftMethod = groovyClosure().withTreeParent(
      groovyBinaryExpression().with(object : PatternCondition<GrBinaryExpression?>("leftShiftCondition") {
        override fun accepts(t: GrBinaryExpression, context: ProcessingContext?): Boolean {
          return t.operationTokenType == COMPOSITE_LSHIFT_SIGN
        }
      }))

    fun getExtensionsFor(psiElement: PsiElement): GradleExtensionsSettings.GradleExtensionsData? {
      val project = psiElement.project
      val virtualFile = psiElement.containingFile?.originalFile?.virtualFile ?: return null
      val module = ProjectFileIndex.SERVICE.getInstance(project).getModuleForFile(virtualFile)
      return GradleExtensionsSettings.getInstance(project).getExtensionsFor(module) ?: return null
    }

    fun getDocumentation(gradleProp: GradleExtensionsSettings.TypeAware,
                         lightVariable: GrLightVariable): String? {
      if (gradleProp is GradleProp) {
        return getDocumentation(gradleProp, lightVariable)
      }
      else if (gradleProp is GradleTask) {
        return getDocumentation(gradleProp, lightVariable)
      }
      else {
        return null
      }
    }

    fun getDocumentation(gradleProp: GradleProp,
                         lightVariable: GrLightVariable): String {
      val buffer = StringBuilder()
      buffer.append("<PRE>")
      JavaDocInfoGenerator.generateType(buffer, lightVariable.type, lightVariable, true)
      buffer.append(" " + gradleProp.name)
      val hasInitializer = !gradleProp.value.isNullOrBlank()
      if (hasInitializer) {
        buffer.append(" = ")
        val longString = gradleProp.value!!.toString().length > 100
        if (longString) {
          buffer.append("<blockquote>")
        }
        buffer.append(gradleProp.value)
        if (longString) {
          buffer.append("</blockquote>")
        }
      }
      buffer.append("</PRE>")
      if (hasInitializer) {
        buffer.append("<br><b>Initial value has been got during last import</b>")
      }
      return buffer.toString()
    }

    fun getDocumentation(gradleTask: GradleTask,
                         lightVariable: GrLightVariable): String {
      val buffer = StringBuilder()
      buffer.append("<PRE>")
      JavaDocInfoGenerator.generateType(buffer, lightVariable.type, lightVariable, true)
      buffer.append(" " + gradleTask.name)
      buffer.append("</PRE>")
      if (!gradleTask.description.isNullOrBlank()) {
        buffer.append(gradleTask.description)
      }
      return buffer.toString()
    }
  }
}

fun processExtension(processor: PsiScopeProcessor,
                     state: ResolveState,
                     place: PsiElement,
                     extension: GradleExtensionsSettings.GradleExtension): Boolean {
  val classHint = processor.getHint(ElementClassHint.KEY)
  val shouldProcessMethods = ResolveUtil.shouldProcessMethods(classHint)
  val groovyPsiManager = GroovyPsiManager.getInstance(place.project)
  val resolveScope = place.resolveScope
  val projectClass = JavaPsiFacade.getInstance(place.project).findClass(GRADLE_API_PROJECT, resolveScope) ?: return true
  val name = processor.getHint(NameHint.KEY)?.getName(state)

  if (name == extension.name) {

    val returnClass = groovyPsiManager.createTypeByFQClassName(extension.rootTypeFqn, resolveScope) ?: return true
    val methodName = if (shouldProcessMethods) extension.name else "get" + extension.name.capitalize()
    val methodBuilder = GrLightMethodBuilder(place.manager, methodName).apply {
      containingClass = projectClass
      returnType = returnClass
    }
    if (shouldProcessMethods) {
      methodBuilder.addParameter("configuration", GROOVY_LANG_CLOSURE, true)
    }
    place.putUserData(RESOLVED_CODE, true)
    if (!processor.execute(methodBuilder, state)) return false
  }

  val extensionClosure = groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, extension.name))
  val placeText = place.text
  val psiElement = psiElement()
  if (psiElement.inside(extensionClosure).accepts(place)) {
    if (shouldProcessMethods && !GradleResolverUtil.processDeclarations(processor, state, place, extension.rootTypeFqn)) {
      return false
    }

    val objectTypeFqn = extension.namedObjectTypeFqn?.let { if (it.isNotBlank()) it else null } ?: return true
    if (place.parent is GrMethodCallExpression) {
      val methodBuilder = GradleResolverUtil.createMethodWithClosure(placeText, objectTypeFqn, null, place)
      if (methodBuilder != null) {
        place.putUserData(RESOLVED_CODE, true)
        if (!processor.execute(methodBuilder, state)) return false
      }
    }
    if (place.parent is GrReferenceExpression || psiElement.withTreeParent(extensionClosure).accepts(place)) {
      val variable = GrLightVariable(place.manager, placeText, objectTypeFqn, place)
      place.putUserData(RESOLVED_CODE, true)
      if (!processor.execute(variable, state)) return false
    }
  }
  return true
}
