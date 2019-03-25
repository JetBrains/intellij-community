// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.service.resolve

import com.intellij.psi.*
import com.intellij.psi.scope.PsiScopeProcessor
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_ARTIFACT_HANDLER
import org.jetbrains.plugins.gradle.service.resolve.GradleCommonClassNames.GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.createType
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil.getJavaLangObject
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames.GROOVY_LANG_CLOSURE
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DELEGATES_TO_KEY
import org.jetbrains.plugins.groovy.lang.resolve.delegatesTo.DELEGATES_TO_STRATEGY_KEY
import org.jetbrains.plugins.groovy.lang.resolve.getName
import org.jetbrains.plugins.groovy.lang.resolve.shouldProcessMethods

class GradleArtifactHandlerContributor : NonCodeMembersContributor() {

  override fun getParentClassName(): String? = GRADLE_API_ARTIFACT_HANDLER

  override fun processDynamicElements(qualifierType: PsiType,
                                      clazz: PsiClass?,
                                      processor: PsiScopeProcessor,
                                      place: PsiElement,
                                      state: ResolveState) {
    if (clazz == null) return
    if (!processor.shouldProcessMethods()) return

    val data = GradleExtensionsContributor.getExtensionsFor(place) ?: return
    val methodName = processor.getName(state)
    val manager = place.manager
    val objectType = getJavaLangObject(place)
    val objectVarargType = PsiEllipsisType(objectType)
    val closureType = createType(GROOVY_LANG_CLOSURE, place)
    val configurableArtifactType = createType(GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT, place)

    // TODO store configurations in a map
    for (configuration in data.configurations) {
      val configurationName = configuration.name ?: continue
      if (methodName != null && configurationName != methodName) continue

      val method1 = GrLightMethodBuilder(manager, configurationName).apply {
        containingClass = clazz
        returnType = configurableArtifactType
        addParameter("artifactNotation", objectType)
        addAndGetParameter("configureClosure", closureType).apply {
          putUserData(DELEGATES_TO_KEY, GRADLE_API_CONFIGURABLE_PUBLISH_ARTIFACT)
          putUserData(DELEGATES_TO_STRATEGY_KEY, 1)
        }
      }
      if (!processor.execute(method1, state)) return

      val method2 = GrLightMethodBuilder(manager, configurationName).apply {
        containingClass = clazz
        returnType = PsiType.NULL
        addParameter("artifactNotation", objectType)
        addParameter("artifactNotations", objectVarargType)
      }
      if (!processor.execute(method2, state)) return
    }
  }
}
