// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.codeInsight.tooling

import com.intellij.java.analysis.OuterModelsModificationTrackerManager
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.removeUserData
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.testIntegration.TestFramework
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
        val calculatedTestFrameworkValue = declaration.getUserData(TEST_FRAMEWORK_NAME_KEY)
        if (calculatedTestFrameworkValue != null && allowSlowOperations) {
            // testframework has been provided on `allowSlowOperations=false` state
            return null
        }
        val testFramework = calculatedTestFrameworkValue?.let { cachedValue ->
            val name = cachedValue.upToDateOrNull?.get() ?: return@let null
            TestFramework.EXTENSION_NAME.extensionList.first { name == it.name }
        } ?: KotlinPsiBasedTestFramework.findTestFramework(declaration, !allowSlowOperations)
        return if (testFramework != null) {
            declaration.putUserData(TEST_FRAMEWORK_NAME_KEY, CachedValuesManager.getManager(declaration.project).createCachedValue {
                CachedValueProvider.Result.create(
                    testFramework.name,
                    OuterModelsModificationTrackerManager.getInstance(declaration.project).tracker
                )
            })
            val urls = calculateUrls(declaration)
            if (urls != null) KotlinTestRunLineMarkerContributor.getTestStateIcon(urls, declaration) else null
        } else {
            declaration.removeUserData(TEST_FRAMEWORK_NAME_KEY)
            if (allowSlowOperations) {
                testIconProvider.getGenericTestIcon(declaration, emptyList())
            } else null
        }
    }

    private fun calculateUrls(declaration: KtNamedDeclaration): List<String>? {
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

    private companion object {
        val TEST_FRAMEWORK_NAME_KEY = Key.create<CachedValue<String>>("TestFramework:name")
    }
}