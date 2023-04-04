// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.tooling

import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.testIntegration.TestFramework
import org.jetbrains.kotlin.asJava.toLightClass
import org.jetbrains.kotlin.asJava.toLightMethods
import org.jetbrains.kotlin.idea.highlighter.KotlinTestRunLineMarkerContributor
import org.jetbrains.kotlin.idea.projectModel.KotlinPlatform
import org.jetbrains.kotlin.idea.testIntegration.framework.KotlinPsiBasedTestFramework
import org.jetbrains.kotlin.platform.impl.JvmIdePlatformKind
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.utils.PathUtil
import javax.swing.Icon

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

    override fun getTestIcon(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): Icon? {
        val urls = calculateUrls(declaration, allowSlowOperations)

        return if (urls != null) {
            KotlinTestRunLineMarkerContributor.getTestStateIcon(urls, declaration)
        } else if (allowSlowOperations) {
            testIconProvider.getGenericTestIcon(declaration, emptyList())
        } else {
            null
        }
    }

    private fun calculateUrls(declaration: KtNamedDeclaration, allowSlowOperations: Boolean): List<String>? {
        val testFramework = TestFramework.EXTENSION_NAME.extensionList.firstOrNull { framework ->
            if (framework is KotlinPsiBasedTestFramework) {
                framework.responsibleFor(declaration)
            } else if (allowSlowOperations) {
                when (declaration) {
                    is KtClassOrObject -> declaration.toLightClass()?.let(framework::isTestClass) ?: false
                    is KtNamedFunction -> declaration.toLightMethods().firstOrNull()?.let { framework.isTestMethod(it, false) } ?: false
                    else -> false
                }
            } else {
                false
            }
        }
        // to filter out irrelevant provider
        if (testFramework == null || allowSlowOperations && testFramework is KotlinPsiBasedTestFramework) return null

        val qualifiedName = when (declaration) {
            is KtClassOrObject -> declaration.fqName?.asString()
            is KtNamedFunction -> declaration.containingClassOrObject?.fqName?.asString()
            else -> null
        } ?: return null

        return when (declaration) {
            is KtClassOrObject -> listOf("java:suite://$qualifiedName")
            is KtNamedFunction -> listOf(
                "java:test://$qualifiedName/${declaration.name}",
                "java:test://$qualifiedName.${declaration.name}"
            )
            else -> null
        }
    }
}