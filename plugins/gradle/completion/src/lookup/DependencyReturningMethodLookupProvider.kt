// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.gradle.completion.lookup

import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.InsertHandler
import com.intellij.codeInsight.completion.InsertionContext
import com.intellij.codeInsight.completion.util.ParenthesesInsertHandler
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.ui.IconManager
import com.intellij.ui.PlatformIcons
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
object DependencyReturningMethodLookupProvider {

  /**
   * Provides lookup elements for methods that could be completed inside arguments of a dependency configuration.
   * These methods return [org.gradle.api.artifacts.Dependency] and have [org.gradle.api.artifacts.dsl.DependencyHandler] as a receiver.
   */
  fun getElements(): List<LookupElement> {
    return listOf(
      lookupForMethod("platform", hasParameters = true),
      lookupForMethod("enforcedPlatform", hasParameters = true),
      lookupForMethod("project", hasParameters = true),
      lookupForMethod("kotlin", hasParameters = true),
      lookupForMethod("embeddedKotlin", hasParameters = true),
      lookupForMethod("testFixtures", hasParameters = true),
      lookupForMethod("files", hasParameters = true),
      lookupForMethod("fileTree", hasParameters = true),
      lookupForMethod("variantOf", hasParameters = true),
      lookupForMethod("gradleApi", hasParameters = false),
      lookupForMethod("gradleTestKit", hasParameters = false),
    )
  }

  private fun lookupForMethod(name: String, hasParameters: Boolean): LookupElement {
    val insertHandler = if (hasParameters) withParamsInsertHandler
    else ParenthesesInsertHandler.getInstance(false)

    return LookupElementBuilder.create(name).withInsertHandler(insertHandler)
      .withIcon(IconManager.getInstance().getPlatformIcon(PlatformIcons.Method)).withTypeText("Dependency returning method")
  }

  private val withParamsInsertHandler = InsertHandler { context: InsertionContext, item: LookupElement ->
    ParenthesesInsertHandler.getInstance(true).handleInsert(context, item)
    AutoPopupController.getInstance(context.project).scheduleAutoPopup(context.editor)
  }
}
