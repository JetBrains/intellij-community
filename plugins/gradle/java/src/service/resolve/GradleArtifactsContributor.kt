// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.openapi.util.Key
import com.intellij.patterns.PatternCondition
import com.intellij.util.ProcessingContext
import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.*
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyMethodResult
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyClosurePattern
import org.jetbrains.plugins.groovy.lang.psi.patterns.groovyClosure
import org.jetbrains.plugins.groovy.lang.psi.patterns.psiMethod
import org.jetbrains.plugins.groovy.lang.psi.patterns.withKind
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DelegatesToInfo

/**
 * @author Vladislav.Soroka
 * */
class GradleArtifactsContributor : GradleMethodContextContributor {

  companion object {
    private val inCurrentProject: Key<Boolean> = Key.create("gradle.current.project")
    val artifactsClosure: GroovyClosurePattern = groovyClosure().inMethod(
      psiMethod(GRADLE_API_PROJECT, "artifacts")
    ).inMethodResult(object : PatternCondition<GroovyMethodResult>("saveProjectContext") {
      override fun accepts(result: GroovyMethodResult, context: ProcessingContext?): Boolean {
        // Given the closure matched Project#artifacts method,
        // we want to determine what we know about this Project.
        // This PatternCondition just saves the info into the ProcessingContext.
        context?.put(inCurrentProject, result.candidate?.receiver is GradleProjectAwareType)
        return true
      }
    })
    val artifactClosure: GroovyClosurePattern = groovyClosure().inMethod(
      psiMethod(GRADLE_API_ARTIFACT_HANDLER).withKind(GradleArtifactHandlerContributor.ourMethodKind)
    )
  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    val context = ProcessingContext()
    if (artifactsClosure.accepts(closure, context)) {
      val type = if (context.get(inCurrentProject) == true) {
        // If Project#artifacts method was invoked on special Project type,
        // then we then want to pass info about project with ArtifactHandler type.
        //
        // Example 1: `artifacts { <here> }`.
        // `artifacts` call is resolved against GradleProjectAwareType in GradleProjectContributor#process.
        // Inside that call we resolve ArtifactHandler declarations from current project later in GradleArtifactHandlerContributor.
        GradleProjectAwareType(GRADLE_API_ARTIFACT_HANDLER, closure)
      }
      else {
        // In other cases we don't know to what project the ArtifactHandler belongs.
        //
        // Example 2: `subprojects { artifacts { <here> } }`
        // While this is perfectly valid code, we don't know what project to resolve against,
        // so we return plain regular delegate type.
        createType(GRADLE_API_ARTIFACT_HANDLER, closure)
      }
      return DelegatesToInfo(type, Closure.DELEGATE_FIRST)
    }
    if (artifactClosure.accepts(closure)) {
      return DelegatesToInfo(createType(GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT, closure), Closure.DELEGATE_FIRST)
    }
    return null
  }
}
