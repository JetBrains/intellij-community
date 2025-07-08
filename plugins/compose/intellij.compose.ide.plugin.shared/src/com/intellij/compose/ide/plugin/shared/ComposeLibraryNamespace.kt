// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
