// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import groovy.lang.Closure
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACT_HANDLER
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT
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
    val artifactClosure: GroovyClosurePattern = groovyClosure().inMethod(
      psiMethod(GRADLE_API_ARTIFACT_HANDLER).withKind(GradleArtifactHandlerContributor.ourMethodKind)
    )
  }

  override fun getDelegatesToInfo(closure: GrClosableBlock): DelegatesToInfo? {
    if (artifactClosure.accepts(closure)) {
      return DelegatesToInfo(createType(GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT, closure), Closure.DELEGATE_FIRST)
    }
    return null
  }
}
