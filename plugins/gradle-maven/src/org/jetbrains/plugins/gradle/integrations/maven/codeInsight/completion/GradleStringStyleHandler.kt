// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral
import runCompletion

class GradleStringStyleGroupIdHandler : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    val element = getLiteral(context) ?: return
    val info = item.`object` as? MavenRepositoryArtifactInfo ?: return
    element.updateText("'${info.groupId}:'")
    runCompletion(element, context)
    context.commitDocument()
  }

  companion object {
    val INSTANCE = GradleStringStyleGroupIdHandler()
  }
}

class GradleStringStyleArtifactIdHandler : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    val element = getLiteral(context) ?: return
    val info = item.`object` as? MavenRepositoryArtifactInfo ?: return
    if(info.items.size ==1){
      element.updateText("'${info.groupId}:${info.artifactId}:${info.version}'")
    } else {
      element.updateText("'${info.groupId}:${info.artifactId}:'")
      runCompletion(element, context)

    }
    context.commitDocument()
  }

  companion object {
    val INSTANCE = GradleStringStyleArtifactIdHandler()
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