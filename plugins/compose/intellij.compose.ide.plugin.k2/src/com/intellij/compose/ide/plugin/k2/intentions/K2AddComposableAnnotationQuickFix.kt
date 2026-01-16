/*
 * Copyright (C) 2023 The Android Open Source Project
 * Modified 2026 by JetBrains s.r.o.
 * Copyright (C) 2026 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compose.ide.plugin.k2.intentions

import androidx.compose.compiler.plugins.kotlin.ComposeClassIds
import com.intellij.compose.ide.plugin.shared.ComposeIdeBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.codeinsight.api.classic.quickfixes.KotlinQuickFixAction
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtModifierListOwner
import org.jetbrains.kotlin.idea.util.addAnnotation

/**
 * QuickFix that adds the `@Composable` annotation
 *
 * Based on: [com.android.tools.compose.intentions.AddComposableAnnotationQuickFix]
 */
internal class K2AddComposableAnnotationQuickFix(element: KtModifierListOwner, private val displayText: String) :
  KotlinQuickFixAction<KtModifierListOwner>(element) {

  override fun getFamilyName(): String = ComposeIdeBundle.message("compose.add.composable.annotation.name")

  override fun getText(): String = displayText

  override fun invoke(project: Project, editor: Editor?, file: KtFile) {
    element?.addAnnotation(ComposeClassIds.Composable)
  }
}