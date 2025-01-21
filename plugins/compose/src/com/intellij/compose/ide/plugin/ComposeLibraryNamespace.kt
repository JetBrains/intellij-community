// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin

import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal val COMPOSABLE_ANNOTATION_NAME: Name = Name.identifier("Composable")
internal val COMPOSABLE_ANNOTATION_FQ_NAME: FqName = FqName("androidx.compose.runtime.$COMPOSABLE_ANNOTATION_NAME")
internal val COMPOSABLE_ANNOTATION_CLASS_ID: ClassId = ClassId.topLevel(COMPOSABLE_ANNOTATION_FQ_NAME)
