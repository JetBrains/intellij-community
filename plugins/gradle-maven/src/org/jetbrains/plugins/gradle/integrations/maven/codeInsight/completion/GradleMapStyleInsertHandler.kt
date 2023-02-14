// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion

import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.lookup.LookupElement
import org.jetbrains.idea.maven.onlinecompletion.model.MavenRepositoryArtifactInfo
import org.jetbrains.idea.maven.statistics.MavenDependencyInsertionCollector
import org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion.MavenDependenciesGradleCompletionContributor.Companion.COMPLETION_DATA_KEY
import org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion.MavenDependenciesGradleCompletionContributor.Companion.CompletionData
import org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion.MavenDependenciesGradleCompletionContributor.Companion.GROUP_LABEL
import org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion.MavenDependenciesGradleCompletionContributor.Companion.NAME_LABEL
import org.jetbrains.plugins.gradle.integrations.maven.codeInsight.completion.MavenDependenciesGradleCompletionContributor.Companion.VERSION_LABEL
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument
import runCompletion


abstract class GradleMapStyleInsertHandler : InsertHandler<LookupElement> {
  override fun handleInsert(context: InsertionContext, item: LookupElement) {
    val file = context.file as? GroovyFile ?: return

    val element = file.findElementAt(context.startOffset)
    val psiElement = element?.parent?.parent as? GrNamedArgument ?: return
    val parent = psiElement.parent as? GrArgumentList ?: return

    val factory = GroovyPsiElementFactory.getInstance(parent.project)
    val artifactInfo = item.`object` as? MavenRepositoryArtifactInfo ?: return

    doInsert(psiElement, parent, factory, artifactInfo, context)

    val selectedLookupIndex = context.elements.indexOf(item)
    val (completionPrefix, _, _) = item.getUserData(COMPLETION_DATA_KEY) ?: CompletionData("", "", '\'')

    MavenDependencyInsertionCollector.logPackageAutoCompleted(
      groupId = artifactInfo.groupId,
      artifactId = artifactInfo.artifactId,
      version = artifactInfo.version ?: "",
      buildSystem = MavenDependencyInsertionCollector.Companion.BuildSystem.GRADLE,
      dependencyDeclarationNotation = MavenDependencyInsertionCollector.Companion.DependencyDeclarationNotation.GRADLE_MAP_STYLE,
      completionPrefixLength = completionPrefix.length,
      selectedLookupIndex = selectedLookupIndex
    )
  }

  abstract fun doInsert(psiElement: GrNamedArgument,
                        parent: GrArgumentList,
                        factory: GroovyPsiElementFactory,
                        artifactInfo: MavenRepositoryArtifactInfo,
                        context: InsertionContext)
}

class GradleMapStyleInsertGroupHandler : GradleMapStyleInsertHandler() {
  override fun doInsert(psiElement: GrNamedArgument,
                        parent: GrArgumentList,
                        factory: GroovyPsiElementFactory,
                        artifactInfo: MavenRepositoryArtifactInfo,
                        context: InsertionContext) {
    setValue(GROUP_LABEL, artifactInfo.groupId, parent, factory)
    val artifactPsi = setValue(NAME_LABEL, "", parent, factory)
    runCompletion(artifactPsi, context)
  }

  companion object {
    val INSTANCE = GradleMapStyleInsertGroupHandler()
  }

}

class GradleMapStyleInsertArtifactIdHandler : GradleMapStyleInsertHandler() {
  override fun doInsert(psiElement: GrNamedArgument,
                        parent: GrArgumentList,
                        factory: GroovyPsiElementFactory,
                        artifactInfo: MavenRepositoryArtifactInfo,
                        context: InsertionContext) {
    setValue(NAME_LABEL, artifactInfo.artifactId, parent, factory)

    if (artifactInfo.items.size != 1) {
      val versionPsi = setValue(VERSION_LABEL, "", parent, factory)
      context.commitDocument()
      runCompletion(versionPsi, context)
    }
    else {
      setValue(VERSION_LABEL, artifactInfo.items[0].version.orEmpty(), parent, factory)
      context.commitDocument()
    }
  }

  companion object {
    val INSTANCE = GradleMapStyleInsertArtifactIdHandler()
  }
}

private fun setValue(name: String, value: String, parent: GrArgumentList, factory: GroovyPsiElementFactory): GrNamedArgument {
  val namedArgument = parent.findNamedArgument(name)
  if (namedArgument != null) {
    namedArgument.expression?.replaceWithExpression(factory.createExpressionFromText("'$value'"), true)
    return namedArgument
  }
  return parent.addAfter(factory.createNamedArgument(name, factory.createExpressionFromText("'$value'")),
                         parent.lastChild) as GrNamedArgument
}
