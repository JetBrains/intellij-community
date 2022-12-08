// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration.mpp

import com.intellij.openapi.externalSystem.model.project.LibraryPathType
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinResolvedBinaryDependency
import org.jetbrains.kotlin.gradle.idea.tcs.IdeaKotlinSourceDependency

val IdeaKotlinSourceDependency.kotlinSourceSetModuleId: KotlinSourceSetModuleId get() = KotlinSourceSetModuleId(coordinates)

val IdeaKotlinBinaryDependency.kotlinLibraryName: KotlinLibraryName? get() = coordinates?.let(::KotlinLibraryName)

internal val IdeaKotlinResolvedBinaryDependency.isClasspathType get() = this.binaryType == IdeaKotlinDependency.CLASSPATH_BINARY_TYPE

internal val IdeaKotlinResolvedBinaryDependency.isSourcesType get() = this.binaryType == IdeaKotlinDependency.SOURCES_BINARY_TYPE

internal val IdeaKotlinResolvedBinaryDependency.isDocumentationType get() = this.binaryType == IdeaKotlinDependency.DOCUMENTATION_BINARY_TYPE

internal val IdeaKotlinResolvedBinaryDependency.libraryPathType: LibraryPathType?
    get() = when {
        isClasspathType -> LibraryPathType.BINARY
        isSourcesType -> LibraryPathType.SOURCE
        isDocumentationType -> LibraryPathType.DOC
        else -> null
    }