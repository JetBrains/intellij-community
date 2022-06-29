// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.tooling

import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.utils.PathUtil

abstract class AbstractJvmIdePlatformKindTooling : IdePlatformKindTooling() {
    override val kind = JvmIdePlatformKind

    override val mavenLibraryIds = listOf(
      PathUtil.KOTLIN_JAVA_STDLIB_NAME,
      PathUtil.KOTLIN_JAVA_RUNTIME_JRE7_NAME,
      PathUtil.KOTLIN_JAVA_RUNTIME_JDK7_NAME,
      PathUtil.KOTLIN_JAVA_RUNTIME_JRE8_NAME,
      PathUtil.KOTLIN_JAVA_RUNTIME_JDK8_NAME
    )

    override val gradlePluginId = "kotlin-platform-jvm"
    override val gradlePlatformIds: List<KotlinPlatform> get() = listOf(KotlinPlatform.JVM, KotlinPlatform.ANDROID)

    override val libraryKind: PersistentLibraryKind<*>? = null

    override fun acceptsAsEntryPoint(function: KtFunction) = true
}