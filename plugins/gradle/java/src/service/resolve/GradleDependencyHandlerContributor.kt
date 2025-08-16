// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import com.intellij.psi.util.parentOfType
import icons.GradleIcons
import org.jetbrains.plugins.gradle.service.completion.GradleLookupWeigher
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_DEPENDENCY_HANDLER
import org.jetbrains.plugins.gradle.settings.GradleExtensionsSettings.GradleConfiguration
import org.jetbrains.plugins.gradle.util.GradleBundle
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder
import org.jetbrains.plugins.groovy.intentions.style.inference.resolve
import org.jetbrains.plugins.groovy.lang.psi.api.GrFunctionalExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GroovyScriptClass
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMethods

class GradleDependencyHandlerContributor : NonCodeMembersContributor() {

  override fun getParentClassName(): String = GRADLE_API_DEPENDENCY_HANDLER

  override fun processDynamicElements(qualifierType: PsiType,
                                      clazz: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (qualifierType !is GradleProjectAwareType) return

    if (clazz == null) return
    if (!processor.shouldProcessMethods()) return

    val data = GradlePropertyExtensionsContributor.getExtensionsFor(place) ?: return
    val methodName = processor.getName(state)
    val manager = place.manager
    val objectVarargType = PsiEllipsisType(TypesUtil.getJavaLangObject(place))

    val configurationsMap = if (qualifierType.buildscript) data.buildScriptConfigurations else data.configurations
    val configurations = if (methodName == null) configurationsMap.values else configurationsMap[methodName]?.let{ listOf(it) } ?: emptyList()

    for (configuration in configurations) {
      val configurationName = configuration.name ?: continue
      if (!addMethod(manager, configurationName, clazz, place, objectVarargType, configuration.getConfigurationDescription(), processor, state, configuration.declarationAlternatives)) return
    }
  }

  private fun addMethod(manager: PsiManager?,
                configurationName: String,
                clazz: PsiClass?,
                place: PsiElement,
                objectVarargType: PsiEllipsisType,
                description: String?,
                processor: PsiScopeProcessor,
                state: ResolveState,
                declarationAlternatives: List<String>): Boolean {
    val method = GrLightMethodBuilder(manager, configurationName).apply {
      methodKind = dependencyMethodKind
      containingClass = clazz
      returnType = TypesUtil.createType(GradleCommonClassNames.GRADLE_API_ARTIFACTS_EXTERNAL_MODULE_DEPENDENCY, place)
      originInfo = DEPENDENCY_NOTATION
      addParameter("dependencyNotation", objectVarargType)
      setBaseIcon(GradleIcons.Gradle)
      if (declarationAlternatives.isNotEmpty()) {
        putUserData(DECLARATION_ALTERNATIVES, declarationAlternatives)
        modifierList.addAnnotation(CommonClassNames.JAVA_LANG_DEPRECATED)
      }
      putUserData(NonCodeMembersHolder.DOCUMENTATION, description)
      if (worthLifting(place)) {
        GradleLookupWeigher.setGradleCompletionPriority(this, GradleLookupWeigher.DEFAULT_COMPLETION_PRIORITY * 2)
      }
    }
    return processor.execute(method, state)
  }

  private fun worthLifting(place: PsiElement): Boolean {
    return place.parentOfType<GrFunctionalExpression>()?.ownerType?.resolve() is GroovyScriptClass
  }

  private fun GradleConfiguration.getConfigurationDescription(): String? {
    if (description == null && isScriptClasspath && name == "classpath") {
      return GradleBundle.message("gradle.codeInsight.buildscript.classpath.description")
    }
    else {
      return description
    }
  }

  companion object {
    internal const val DEPENDENCY_NOTATION : String = "by Gradle, configuration method"
    const val dependencyMethodKind: String = "gradle:dependencyMethod"
  }
}
