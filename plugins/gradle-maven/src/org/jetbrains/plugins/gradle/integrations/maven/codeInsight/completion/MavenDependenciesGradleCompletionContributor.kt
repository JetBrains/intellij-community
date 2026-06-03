// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionService
import com.intellij.codeInsight.completion.CompletionSorter
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.CompletionUtil
import com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER
import com.intellij.codeInsight.completion.CompletionUtil.DUMMY_IDENTIFIER_TRIMMED
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.gradle.java.groovy.codeInsight.AbstractGradleGroovyCompletionContributor
import com.intellij.maven.completion.getCompletionContext
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.patterns.PatternCondition
import com.intellij.patterns.PlatformPatterns.psiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.repository.search.completion.api.DependencyArtifactCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionEvent
import com.intellij.repository.search.completion.api.DependencyCompletionRequest
import com.intellij.repository.search.completion.api.DependencyCompletionService
import com.intellij.repository.search.completion.api.DependencyVersionCompletionRequest
import com.intellij.util.ProcessingContext
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.dom.model.completion.MavenVersionNegatingWeigher
import org.jetbrains.idea.maven.model.MavenDependencyCompletionItem
import org.jetbrains.idea.maven.model.MavenRepoArtifactInfo
import org.jetbrains.plugins.groovy.lang.completion.GrDummyIdentifierProvider.DUMMY_IDENTIFIER_DECAPITALIZED
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCallExpression
import org.jetbrains.plugins.groovy.lang.psi.api.util.GrNamedArgumentsOwner

/**
 * @author Vladislav.Soroka
 */
class MavenDependenciesGradleCompletionContributor : AbstractGradleGroovyCompletionContributor() {
  init {
    // map-style notation:
    // e.g.:
    //    compile group: 'com.google.code.guice', name: 'guice', version: '1.0'
    //    runtime([group:'junit', name:'junit-dep', version:'4.7'])
    //    compile(group:'junit', name:'junit-dep', version:'4.7')
    extend(CompletionType.BASIC, IN_MAP_DEPENDENCY_NOTATION, object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(
        params: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
      ) {
        val parent = params.position.parent?.parent
        if (parent !is GrNamedArgument || parent.parent !is GrNamedArgumentsOwner) {
          return
        }
        result.stopHere()
        result.restartCompletionOnAnyPrefixChange()

        val completionContext = params.getCompletionContext()
        if (GROUP_LABEL == parent.labelName) {
          val groupId = trimDummy(findNamedArgumentValue(parent.parent as GrNamedArgumentsOwner, GROUP_LABEL))
          runBlockingCancellable {
            val seen = mutableSetOf<String>()
            service<DependencyCompletionService>()
              .suggestCompletions(DependencyCompletionRequest(groupId, completionContext))
              .collect { event ->
                if (event !is DependencyCompletionEvent.Item) return@collect
                val depResult = event.result
                if (seen.add(depResult.groupId)) {
                  result.addElement(
                    MavenDependencyCompletionUtil.lookupElement(
                      MavenRepoArtifactInfo(depResult.groupId, depResult.artifactId, emptyList())
                    ).withInsertHandler(GradleMapStyleInsertGroupHandler.INSTANCE)
                  )
                }
              }
          }
        }
        else if (NAME_LABEL == parent.labelName) {
          val groupId = trimDummy(findNamedArgumentValue(parent.parent as GrNamedArgumentsOwner, GROUP_LABEL))
          val artifactId = trimDummy(findNamedArgumentValue(parent.parent as GrNamedArgumentsOwner, NAME_LABEL))
          runBlockingCancellable {
            if (groupId.isBlank()) {
              service<DependencyCompletionService>()
                .suggestCompletions(DependencyCompletionRequest(artifactId, completionContext))
                .collect { event ->
                  if (event !is DependencyCompletionEvent.Item) return@collect
                  val depResult = event.result
                  result.addElement(
                    MavenDependencyCompletionUtil.lookupElement(
                      MavenRepoArtifactInfo(depResult.groupId, depResult.artifactId, emptyList())
                    ).withInsertHandler(GradleMapStyleInsertArtifactIdHandler.INSTANCE)
                  )
                }
            }
            else {
              service<DependencyCompletionService>()
                .suggestArtifactCompletions(DependencyArtifactCompletionRequest(groupId, artifactId, completionContext))
                .collect { event ->
                  if (event !is DependencyCompletionEvent.Item) return@collect
                  val depResult = event.result
                  result.addElement(
                    MavenDependencyCompletionUtil.lookupElement(
                      MavenRepoArtifactInfo(groupId, depResult.result, emptyList())
                    ).withInsertHandler(GradleMapStyleInsertArtifactIdHandler.INSTANCE)
                  )
                }
            }
          }
        }
        else if (VERSION_LABEL == parent.labelName) {
          val groupId = trimDummy(findNamedArgumentValue(parent.parent as GrNamedArgumentsOwner, GROUP_LABEL))
          val artifactId = trimDummy(findNamedArgumentValue(parent.parent as GrNamedArgumentsOwner, NAME_LABEL))
          val newResult = result.withRelevanceSorter(CompletionService.getCompletionService().emptySorter().weigh(MavenVersionNegatingWeigher()))
          runBlockingCancellable {
            service<DependencyCompletionService>()
              .suggestVersionCompletions(DependencyVersionCompletionRequest(groupId, artifactId, "", completionContext))
              .collect { event ->
                if (event !is DependencyCompletionEvent.Item) return@collect
                newResult.addElement(LookupElementBuilder.create(event.result.result))
              }
          }
        }
      }
    })

    // group:name:version notation
    // e.g.:
    //    compile 'junit:junit:4.11'
    //    compile('junit:junit:4.11')
    extend(CompletionType.BASIC, IN_METHOD_DEPENDENCY_NOTATION, object : CompletionProvider<CompletionParameters>() {
      override fun addCompletions(
        params: CompletionParameters,
        context: ProcessingContext,
        result: CompletionResultSet,
      ) {
        val element = params.position.parent
        if (element !is GrLiteral || element.parent !is GrArgumentList) {
          //try
          val parent = element?.parent
          if (parent !is GrLiteral || parent.parent !is GrArgumentList) return
        }

        result.stopHere()

        val completionPrefix = CompletionUtil.findReferenceOrAlphanumericPrefix(params)
        val quote = params.position.text.firstOrNull() ?: '\''
        val suffix = params.originalPosition?.text?.let { StringUtil.unquoteString(it).substring(completionPrefix.length) }

        val splitted = completionPrefix.split(":")
        val groupId = splitted[0]
        val artifactId = splitted.getOrNull(1)
        val version = splitted.getOrNull(2)
        val completionContext = params.getCompletionContext()
        result.restartCompletionOnAnyPrefixChange()
        val additionalData = CompletionData(completionPrefix, suffix, quote)
        if (version != null) {
          val newResult = result.withRelevanceSorter(CompletionSorter.emptySorter().weigh(MavenVersionNegatingWeigher()))
          runBlockingCancellable {
            service<DependencyCompletionService>()
              .suggestVersionCompletions(DependencyVersionCompletionRequest(groupId, artifactId ?: "", version, completionContext))
              .collect { event ->
                if (event !is DependencyCompletionEvent.Item) return@collect
                val depResult = event.result
                val item = MavenDependencyCompletionItem(groupId, artifactId, depResult.result)
                newResult.addElement(
                  MavenDependencyCompletionUtil.lookupElement(item, MavenDependencyCompletionUtil.getLookupString(item))
                    .withPresentableText(depResult.result)
                    .withInsertHandler(GradleStringStyleVersionHandler)
                    .also { it.putUserData(COMPLETION_DATA_KEY, additionalData) }
                )
              }
          }
        }
        else {
          runBlockingCancellable {
            service<DependencyCompletionService>()
              .suggestCompletions(DependencyCompletionRequest(completionPrefix, completionContext))
              .collect { event ->
                if (event !is DependencyCompletionEvent.Item) return@collect
                val depResult = event.result
                val info = MavenRepoArtifactInfo(depResult.groupId, depResult.artifactId, emptyList())
                result.addElement(
                  MavenDependencyCompletionUtil.lookupElement(info)
                    .withInsertHandler(GradleStringStyleGroupAndArtifactHandler)
                    .also { it.putUserData(COMPLETION_DATA_KEY, additionalData) }
                )
              }
          }
        }
      }
    })
  }

  companion object {
    internal const val GROUP_LABEL = "group"
    internal const val NAME_LABEL = "name"
    internal const val VERSION_LABEL = "version"
    internal const val DEPENDENCIES_SCRIPT_BLOCK = "dependencies"

    data class CompletionData(val completionPrefix: String, val suffix: String?, val quote: Char)

    val COMPLETION_DATA_KEY = Key.create<CompletionData>("COMPLETION_DATA")

    private val DEPENDENCIES_CALL_PATTERN = psiElement()
      .inside(true, psiElement(GrMethodCallExpression::class.java).with(
        object : PatternCondition<GrMethodCallExpression>("withInvokedExpressionText") {
          override fun accepts(expression: GrMethodCallExpression, context: ProcessingContext): Boolean {
            if (checkExpression(expression)) return true
            return checkExpression(PsiTreeUtil.getParentOfType(expression, GrMethodCallExpression::class.java))
          }

          private fun checkExpression(expression: GrMethodCallExpression?): Boolean {
            if (expression == null) return false
            val grExpression = expression.invokedExpression
            return DEPENDENCIES_SCRIPT_BLOCK == grExpression.text
          }
        }))

    private val IN_MAP_DEPENDENCY_NOTATION = psiElement()
      .and(GRADLE_FILE_PATTERN)
      .withParent(GrLiteral::class.java)
      .withSuperParent(2, psiElement(GrNamedArgument::class.java))
      .and(DEPENDENCIES_CALL_PATTERN)

    private val IN_METHOD_DEPENDENCY_NOTATION = psiElement()
      .and(GRADLE_FILE_PATTERN)
      .and(DEPENDENCIES_CALL_PATTERN)

    private fun trimDummy(value: String?): String {
      return if (value == null) {
        ""
      }
      else StringUtil.trim(value.replace(DUMMY_IDENTIFIER, "")
                             .replace(DUMMY_IDENTIFIER_TRIMMED, "")
                             .replace(DUMMY_IDENTIFIER_DECAPITALIZED, ""))
    }
  }
}

