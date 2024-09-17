// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.gradleJava.configuration

import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.DependencyScope
import com.intellij.openapi.roots.DependencyScope.*
import org.jetbrains.kotlin.idea.base.externalSystem.KotlinBuildSystemFacade
import org.jetbrains.kotlin.idea.base.facet.isMultiPlatformModule
import org.jetbrains.kotlin.idea.gradleJava.kotlinGradlePluginVersion
import org.jetbrains.kotlin.idea.gradleTooling.compareTo
import org.jetbrains.kotlin.psi.KtBlockExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion


/**
 * In KMP build-scripts, some source sets have 'static accessors'
 * for example,
 * ```kotlin
 * kotlin {
 *     sourceSets.commonMain.dependencies {
 *               //   ^
 *               // Static Accessor/Convention Source Set
 *     }
 * }
 * ```
 *
 * This method will try to use one of those static accessors to add the dependency.
 * Will return `true` if a static accessor was used.
 * Will return `false` if the dependency can't be added using a static accessor
 */
internal fun KotlinBuildScriptManipulator.addKotlinMultiplatformDependencyWithConventionSourceSets(
    module: Module, scope: DependencyScope,
    libraryGroupId: String, libraryArtifactId: String, libraryVersion: String?,
): Boolean {
    /* Guards */
    if (!module.isMultiPlatformModule) return false
    val kotlinGradlePluginVersion = module.kotlinGradlePluginVersion ?: return false
    val sourceSetName = KotlinBuildSystemFacade.getInstance().findSourceSet(module)?.name ?: return false
    val knownSince = isStaticallyAccessibleSince(sourceSetName) ?: return false
    if (kotlinGradlePluginVersion >= knownSince) {
        return addKotlinMultiplatformDependencyToKnownSourceSet(
            sourceSetName, scope,
            libraryGroupId, libraryArtifactId, libraryVersion,
        )
    }

    return false
}

private fun KotlinBuildScriptManipulator.addKotlinMultiplatformDependencyToKnownSourceSet(
    sourceSetName: String, scope: DependencyScope,
    libraryGroupId: String, libraryArtifactId: String, libraryVersion: String?,
): Boolean {
    val kotlinBlock = scriptFile.getKotlinBlock() ?: return false

    val dependenciesBlock = findSourceSetDependenciesBlock(kotlinBlock, sourceSetName)
        ?: createSourceSetDependenciesBlock(kotlinBlock, sourceSetName) ?: return false

    val dependencyExpression = getCompileDependencySnippet(
        libraryGroupId, libraryArtifactId, libraryVersion, when (scope) {
            /* The source set already includes Main vs. Test scoping (commonMain vs commonTest) */
            COMPILE, TEST -> "implementation"
            RUNTIME -> "runtimeOnly"
            PROVIDED -> "compileOnly"
        }
    )

    dependenciesBlock.addExpressionIfMissing(dependencyExpression)
    return true
}

private fun KotlinBuildScriptManipulator.findSourceSetDependenciesBlock(
    kotlinBlock: KtBlockExpression, sourceSetName: String
): KtBlockExpression? {
    return kotlinBlock
        .statements.filterIsInstance<KtDotQualifiedExpression>()
        .filter { it.receiverExpression.text == "sourceSets.${sourceSetName}" }
        .mapNotNull { expression -> expression.selectorExpression as? KtCallExpression }
        .firstOrNull { expression -> expression.calleeExpression?.text == "dependencies" }
        ?.getBlock()
    return null
}

private fun KotlinBuildScriptManipulator.createSourceSetDependenciesBlock(
    kotlinBlock: KtBlockExpression, sourceSetName: String
): KtBlockExpression? {
    val expression = kotlinBlock.addExpressionIfMissing("sourceSets.$sourceSetName.dependencies{\n}")
    return expression.findDescendantOfType<KtBlockExpression>()
}

/*
Internals
 */

private val knownSince1920 = setOf(
    "commonMain",
    "commonTest",
    "nativeMain",
    "nativeTest",
    "appleMain",
    "appleTest",
    "iosMain",
    "iosTest",
    "macosMain",
    "macosTest",
    "watchosMain",
    "watchosTest",
    "tvosMain",
    "tvosTest",
    "linuxMain",
    "linuxTest",
    "mingwMain",
    "mingwTest",
    "androidMain",
    "androidTest",
    "androidNativeMain",
    "androidNativeTest"
)

private val knownSince2020 = setOf(
    "jvmMain",
    "jvmTest",
    "iosX64Main",
    "iosX64Test",
    "iosArm64Main",
    "iosArm64Test",
    "iosSimulatorArm64Main",
    "iosSimulatorArm64Test",
    "macosX64Main",
    "macosX64Test",
    "macosArm64Main",
    "macosArm64Test",
    "watchosX64Main",
    "watchosX64Test",
    "watchosArm32Main",
    "watchosArm32Test",
    "androidMain",
    "androidInstrumentedTest",
    "androidUnitTest",
)

/**
 * In KMP build-scripts, some source sets have 'static accessors'
 * e.g.,
 * ```kotlin
 * kotlin {
 *     sourceSets.commonMain.dependencies {
 *               //   ^
 *               // Static Accessor
 *     }
 * }
 * ```
 */
private fun isStaticallyAccessibleSince(sourceSetName: String): KotlinToolingVersion? {
    if (sourceSetName in knownSince1920) return KotlinToolingVersion("1.9.20")
    if (sourceSetName in knownSince2020) return KotlinToolingVersion("2.0.20")
    return null
}