// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils.*
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement

class GroovyVersionAnnotator : Annotator {

  override fun annotate(element: PsiElement, holder: AnnotationHolder) {
    if (element !is GroovyPsiElement) {
      return
    }
    val config = GroovyConfigUtils.getInstance()
    val version = config.getSDKVersion(holder.currentAnnotationSession.file)
    if (version == NO_VERSION) {
      return
    }
    if (version < GROOVY1_7) {
      element.accept(GroovyAnnotatorPre17(holder, version))
    }
    if (version < GROOVY1_8) {
      element.accept(GroovyAnnotatorPre18(holder, version))
    }
    else {
      element.accept(GroovyAnnotator18(holder))
    }
    if (version < GROOVY2_3) {
      element.accept(GroovyAnnotatorPre23(holder, version))
    }
    if (version >= GROOVY2_5) {
      element.accept(GroovyAnnotator25(holder))
    }
    if (version < GROOVY3_0) {
      element.accept(GroovyAnnotatorPre30(holder))
    }
    else {
      element.accept(GroovyAnnotator30(holder))
    }
  }
}
