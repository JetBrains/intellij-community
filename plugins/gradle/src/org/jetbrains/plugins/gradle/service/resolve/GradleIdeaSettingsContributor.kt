// Copyright 2000-2017 JetBrains s.r.o.
// Use of this source code is governed by the Apache 2.0 license that can be
// found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.externalSystem.service.project.settings.ConfigurationDataService
import com.intellij.openapi.util.registry.Registry
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.ResolveState
import com.intellij.psi.scope.PsiScopeProcessor
import groovy.lang.Closure
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DELEGATES_TO_KEY
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DELEGATES_TO_STRATEGY_KEY
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo

/**
 * Created by Nikita.Skvortsov
 * date: 28.09.2017.
 */
class GradleIdeaSettingsContributor: GradleMethodContextContributor {
  companion object {
    val PROJECT_SETTINGS_FQN = "org.jetbrains.gradle.ext.ProjectSettings"
    val MODULE_SETTINGS_FQN = "org.jetbrains.gradle.ext.ModuleSettings"

    val compilerSettingsClosure = groovyClosure().inMethod(psiMethod(PROJECT_SETTINGS_FQN, "compiler"))
    val runConfigurationsClosure = groovyClosure().inMethod(psiMethod(MODULE_SETTINGS_FQN, "runConfigurations"))
    val facetsClosure = groovyClosure().inMethod(psiMethod(MODULE_SETTINGS_FQN, "facets"))

  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? = when {
    compilerSettingsClosure.accepts(closure) -> DelegatesToInfo(
      TypesUtil.createType("org.jetbrains.gradle.ext.IdeaCompilerConfiguration", closure), Closure.DELEGATE_FIRST)
    runConfigurationsClosure.accepts(closure) ->
      DelegatesToInfo(TypesUtil.createGenericType("org.gradle.api.PolymorphicDomainObjectContainer", closure,
                                                  TypesUtil.createType("org.jetbrains.gradle.ext.runConfigurations.RunConfiguration",
                                                                       closure)), Closure.DELEGATE_FIRST)
    facetsClosure.accepts(closure) ->
      DelegatesToInfo(TypesUtil.createGenericType("org.gradle.api.PolymorphicDomainObjectContainer", closure,
                                                  TypesUtil.createType("org.jetbrains.gradle.ext.facets.Facet", closure)),
                      Closure.DELEGATE_FIRST)

    else -> null
  }

  override fun process(methodCallInfo: MutableList<String>, processor: PsiScopeProcessor, state: ResolveState, place: PsiElement): Boolean {
    if (!Registry.`is`(ConfigurationDataService.EXTERNAL_SYSTEM_CONFIGURATION_IMPORT_ENABLED)) {
      return true
    }

    val resolveScope = place.resolveScope

    if (psiElement().inside(GradleIdeaPluginScriptContributor.ideaClosure).accepts(place)) {
      when {
        methodCallInfo.contains("project") -> {
          val ideaProjectClass = JavaPsiFacade.getInstance(place.project).findClass(GradleIdeaPluginScriptContributor.IDEA_PROJECT_FQN, resolveScope) ?: return true
          val projectSettingsMethodBuilder = GrLightMethodBuilder(place.manager, "settings").apply {
            containingClass = ideaProjectClass
            returnType = PsiType.VOID
            addAndGetParameter("configuration", GroovyCommonClassNames.GROOVY_LANG_CLOSURE, false).apply {
              putUserData(DELEGATES_TO_KEY, PROJECT_SETTINGS_FQN)
              putUserData(DELEGATES_TO_STRATEGY_KEY, Closure.DELEGATE_FIRST)
            }
          }

          if (!processor.execute(projectSettingsMethodBuilder, state)) return false
        }

        methodCallInfo.contains("module")  -> {
          val ideaModuleClass = JavaPsiFacade.getInstance(place.project).findClass(GradleIdeaPluginScriptContributor.IDEA_MODULE_FQN, resolveScope) ?: return true
          val moduleSettingsMethodBuilder = GrLightMethodBuilder(place.manager, "settings").apply {
            containingClass = ideaModuleClass
            returnType = PsiType.VOID
            addAndGetParameter("configuration", GroovyCommonClassNames.GROOVY_LANG_CLOSURE, false).apply {
              putUserData(DELEGATES_TO_KEY, MODULE_SETTINGS_FQN)
              putUserData(DELEGATES_TO_STRATEGY_KEY, Closure.DELEGATE_FIRST)
            }
          }

          if (!processor.execute(moduleSettingsMethodBuilder, state)) return false
        }
      }
    }
    return super.process(methodCallInfo, processor, state, place)
  }
}