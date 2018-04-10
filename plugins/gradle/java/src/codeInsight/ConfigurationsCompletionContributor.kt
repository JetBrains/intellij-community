// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.codeInsight

import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.PsiElement
import com.intellij.util.ProcessingContext
import icons.GradleIcons
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.gradle.service.resolve.GradleExtensionsContributor
import org.jetbrains.plugins.groovy.dsl.holders.NonCodeMembersHolder
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightVariable
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import java.lang.Double.MAX_VALUE

/**
 * @author Vladislav.Soroka
 * @since 12/7/2016
 */
class ConfigurationsCompletionContributor : AbstractGradleCompletionContributor() {

  class ConfigurationsCompletionProvider(val isScriptClasspath: Boolean = false) : CompletionProvider<CompletionParameters>() {

    override fun addCompletions(params: CompletionParameters,
                                context: ProcessingContext,
                                result: CompletionResultSet) {
      val position = params.position
      val extensionsData = GradleExtensionsContributor.getExtensionsFor(position) ?: return
      for (configuration in extensionsData.configurations) {
        if (isScriptClasspath != configuration.scriptClasspath) continue

        val confVar = object : GrLightVariable(position.manager, configuration.name, GRADLE_API_CONFIGURATION, position) {
          override fun getNavigationElement(): PsiElement {
            val navigationElement = super.getNavigationElement()
            val description = if (isScriptClasspath && configuration.description == null) DEFAULT_SCRIPT_CLASSPATH_DESCRIPTION else configuration.description
            navigationElement.putUserData(NonCodeMembersHolder.DOCUMENTATION, description)
            return navigationElement
          }
        }
        val elementBuilder = LookupElementBuilder.create(confVar, configuration.name)
          .withIcon(GradleIcons.Gradle)
          .withTypeText("Configuration")
        result.addElement(PrioritizedLookupElement.withPriority(elementBuilder, MAX_VALUE))
      }
    }
  }

  init {
    extend(CompletionType.BASIC, PROJECT_DEPENDENCIES_PLACE_PATTERN, ConfigurationsCompletionProvider())
    extend(CompletionType.BASIC, SCRIPT_DEPENDENCIES_PLACE_PATTERN, ConfigurationsCompletionProvider(true))
  }

  companion object {

    private const val DEFAULT_SCRIPT_CLASSPATH_DESCRIPTION = "The script classpath configuration used to compile and execute a build script. " +
                                                             "This classpath is also used to load the plugins which the build script uses."

    private val PROJECT_DEPENDENCIES_PLACE_PATTERN = psiElement()
      .and(GRADLE_FILE_PATTERN)
      .withParent(GrReferenceExpression::class.java)
      .withAncestor(4, groovyClosure().inMethod(psiMethod(GRADLE_API_PROJECT, "dependencies")))

    private val SCRIPT_DEPENDENCIES_PLACE_PATTERN = psiElement()
      .and(GRADLE_FILE_PATTERN)
      .withParent(GrReferenceExpression::class.java)
      .withAncestor(4, groovyClosure().inMethod(psiMethod(GRADLE_API_SCRIPT_HANDLER, "dependencies")))
  }
}