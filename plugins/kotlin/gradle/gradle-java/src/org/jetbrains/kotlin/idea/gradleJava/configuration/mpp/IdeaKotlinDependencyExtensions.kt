// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency

val IdeaKotlinSourceDependency.kotlinSourceSetModuleId: KotlinSourceSetModuleId get() = KotlinSourceSetModuleId(coordinates)

val IdeaKotlinBinaryDependency.kotlinLibraryName: KotlinLibraryName? get() = coordinates?.let(::KotlinLibraryName)