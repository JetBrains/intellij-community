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
