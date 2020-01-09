// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationBuilder
import com.intellij.openapi.util.TextRange

class AnnotationFixRegistrar(private val annotation: AnnotationBuilder) : QuickFixActionRegistrar {

  override fun register(action: IntentionAction) {
    annotation.withFix(action)
  }

  override fun register(fixRange: TextRange, action: IntentionAction, key: HighlightDisplayKey?) {
    val fixBuilder = annotation.newFix(action).range(fixRange)
    if (key != null) {
      fixBuilder.key(key)
    }
    fixBuilder.registerFix()
  }
}
