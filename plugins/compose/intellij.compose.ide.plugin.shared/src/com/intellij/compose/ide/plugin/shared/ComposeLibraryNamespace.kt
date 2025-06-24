/*
 * Copyright (C) 2020 The Android Open Source Project
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

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

val COMPOSABLE_ANNOTATION_NAME: Name = Name.identifier("Composable")
val COMPOSABLE_ANNOTATION_FQ_NAME: FqName = FqName("androidx.compose.runtime.$COMPOSABLE_ANNOTATION_NAME")
val COMPOSABLE_ANNOTATION_CLASS_ID: ClassId = ClassId.topLevel(COMPOSABLE_ANNOTATION_FQ_NAME)
val COMPOSE_MODIFIER_NAME: Name = Name.identifier("Modifier")
val COMPOSE_MODIFIER_FQN: FqName = FqName("androidx.compose.ui.$COMPOSE_MODIFIER_NAME")
val COMPOSE_MODIFIER_CLASS_ID: ClassId = ClassId.topLevel(COMPOSE_MODIFIER_FQN)
val DISALLOW_COMPOSABLE_CALLS_FQ_NAME: FqName = FqName("androidx.compose.runtime.DisallowComposableCalls")

val PREVIEW_CLASS_NAME: Name = Name.identifier("Preview")
val PREVIEW_PARAMETER_CLASS_NAME: Name = Name.identifier("PreviewParameter")

const val MULTIPLATFORM_PREVIEW_TOOLING_PACKAGE: String = "org.jetbrains.compose.ui.tooling.preview"

val MULTIPLATFORM_PREVIEW_FQ_NAME: FqName = FqName("$MULTIPLATFORM_PREVIEW_TOOLING_PACKAGE.$PREVIEW_CLASS_NAME")
val MULTIPLATFORM_PREVIEW_CLASS_ID: ClassId = ClassId.topLevel(MULTIPLATFORM_PREVIEW_FQ_NAME)
val MULTIPLATFORM_PREVIEW_PARAMETER_FQ_NAME: FqName = FqName("$MULTIPLATFORM_PREVIEW_TOOLING_PACKAGE.$PREVIEW_PARAMETER_CLASS_NAME")
val MULTIPLATFORM_PREVIEW_PARAMETER_CLASS_ID: ClassId = ClassId.topLevel(MULTIPLATFORM_PREVIEW_PARAMETER_FQ_NAME)

const val JETPACK_PREVIEW_TOOLING_PACKAGE: String = "androidx.compose.ui.tooling.preview"

val JETPACK_PREVIEW_FQ_NAME: FqName = FqName("$JETPACK_PREVIEW_TOOLING_PACKAGE.$PREVIEW_CLASS_NAME")
val JETPACK_PREVIEW_CLASS_ID: ClassId = ClassId.topLevel(JETPACK_PREVIEW_FQ_NAME)
val JETPACK_PREVIEW_PARAMETER_FQ_NAME: FqName = FqName("$JETPACK_PREVIEW_TOOLING_PACKAGE.$PREVIEW_PARAMETER_CLASS_NAME")
val JETPACK_PREVIEW_PARAMETER_CLASS_ID: ClassId = ClassId.topLevel(JETPACK_PREVIEW_PARAMETER_FQ_NAME)
