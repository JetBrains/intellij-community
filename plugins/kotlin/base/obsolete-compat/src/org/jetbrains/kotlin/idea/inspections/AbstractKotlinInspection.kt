// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.inspections

import org.jetbrains.kotlin.K1Deprecation


@K1Deprecation
@Deprecated("Please use org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection")
abstract class AbstractKotlinInspection : org.jetbrains.kotlin.idea.codeinsight.api.classic.inspections.AbstractKotlinInspection()
