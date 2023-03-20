// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion

import com.intellij.codeInsight.completion.CodeCompletionHandlerBase
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.idea.maven.dom.converters.MavenDependencyCompletionUtil
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.maven.statistics.MavenDependencyInsertionCollector
import org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion.MavenDependenciesGradleCompletionContributor.Companion.COMPLETION_DATA_KEY
import org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion.MavenDependenciesGradleCompletionContributor.Companion.CompletionData
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral

abstract class ReplaceEndInsertHandler : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    val element = getLiteral(context) ?: return
    val completed = getCompletedString(item) ?: return
    val (completionPrefix, suffix, quote) = item.getUserData(COMPLETION_DATA_KEY) ?: CompletionData("", "", '\'')
    val insertedSuffix = if (context.completionChar == Lookup.REPLACE_SELECT_CHAR) "" else suffix.orEmpty()
    val newText = completed + insertedSuffix
    element.updateText("${quote}$newText${quote}")
    postProcess(completed, element.textRange.endOffset - (insertedSuffix.length + 1), context)
    context.commitDocument()

    val selectedLookupIndex = context.elements.indexOf(item)
    val artifactInfo = item.`object` as? MavenRepositoryArtifactInfo ?: return

    MavenDependencyInsertionCollector.logPackageAutoCompleted(
      groupId = artifactInfo.groupId,
      artifactId = artifactInfo.artifactId,
      version = artifactInfo.version ?: "",
      buildSystem = MavenDependencyInsertionCollector.Companion.BuildSystem.GRADLE,
      dependencyDeclarationNotation = MavenDependencyInsertionCollector.Companion.DependencyDeclarationNotation.GRADLE_STRING_STYLE,
      completionPrefixLength = completionPrefix.length,
      selectedLookupIndex = selectedLookupIndex
    )
  }

  abstract fun getCompletedString(item: LookupElement): String?
  open fun postProcess(completedString: String, completedStringEndOffset: Int, context: InsertionContext) {}
}

object GradleStringStyleVersionHandler : ReplaceEndInsertHandler() {
  override fun getCompletedString(item: LookupElement): String? {
    return (item.`object` as? MavenRepositoryArtifactInfo?)?.version
  }
}

object GradleStringStyleGroupAndArtifactHandler : ReplaceEndInsertHandler() {
  override fun getCompletedString(item: LookupElement): String? {
    val info = item.`object` as? MavenRepositoryArtifactInfo ?: return null
    val completed = MavenDependencyCompletionUtil.getPresentableText(info)
    val moreCompletionNeeded = completed.count { it == ':' } < 2
    return completed + if (moreCompletionNeeded) ":" else ""
  }

  override fun postProcess(completedString: String, completedStringEndOffset: Int, context: InsertionContext) {
    if (!completedString.endsWith(':')) return
    context.editor.caretModel.moveToOffset(completedStringEndOffset)
    context.setLaterRunnable { CodeCompletionHandlerBase(CompletionType.BASIC).invokeCompletion(context.project, context.editor) }
  }
}

private fun getLiteral(context: InsertionContext): GrLiteral? {
  val file = context.file as? GroovyFile ?: return null
  val psiElement = file.findElementAt(context.startOffset) ?: return null
  if (psiElement is GrLiteral && psiElement.parent is GrArgumentList) {
    return psiElement
  }
  val parent = psiElement.parent
  if (parent is GrLiteral && parent.parent is GrArgumentList) {
    return parent
  }
  return null
}