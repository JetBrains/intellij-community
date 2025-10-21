/*
 * Copyright (C) 2022 The Android Open Source Project
 * Modified 2025 by JetBrains s.r.o.
 * Copyright (C) 2025 JetBrains s.r.o.
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
package com.intellij.compose.ide.plugin.shared

import com.intellij.codeInsight.generation.OverrideImplementsAnnotationsFilter
import com.intellij.psi.PsiFile
import org.jetbrains.kotlin.psi.KtFile

/**
 * Extension for [com.intellij.codeInsight.generation.OverrideImplementsAnnotationsFilter], which checks if the "Composable" annotation is on the classpath,
 * and if that's the case - retains it while doing overrides.
 * This is more generic than the Android's `com.android.tools.compose.ComposeOverrideImplementsAnnotationsFilter` that only checks
 * module's `usesCompose` flag, which only works for Android modules, but not for multiplatform.
 */
internal class ComposeOverrideImplementsAnnotationsFilter : OverrideImplementsAnnotationsFilter {
  override fun getAnnotations(file: PsiFile): Array<String> {
    return if (file is KtFile && isKotlinClassAvailable(file, COMPOSABLE_ANNOTATION_CLASS_ID)) {
      arrayOf(COMPOSABLE_ANNOTATION_FQ_NAME.asString())
    } else {
      arrayOf()
    }
  }
}
