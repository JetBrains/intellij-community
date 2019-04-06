// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.annotator

import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInsight.daemon.QuickFixActionRegistrar
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.Annotation
import com.intellij.openapi.util.TextRange

class AnnotationFixRegistrar(private val annotation: Annotation) : QuickFixActionRegistrar {

  override fun register(action: IntentionAction) {
    annotation.registerFix(action)
  }

  override fun register(fixRange: TextRange, action: IntentionAction, key: HighlightDisplayKey?) {
    annotation.registerFix(action, fixRange, key)
  }
}
