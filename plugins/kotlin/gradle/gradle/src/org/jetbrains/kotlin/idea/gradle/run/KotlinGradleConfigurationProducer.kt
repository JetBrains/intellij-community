// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.gradle.run

import com.intellij.openapi.module.Module

interface KotlinGradleConfigurationProducer {
    val forceGradleRunner: Boolean
    val hasTestFramework: Boolean
    fun isApplicable(module: Module): Boolean
}