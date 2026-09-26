// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.multiplatformTests.junit5

import org.gradle.util.GradleVersion
import org.jetbrains.kotlin.idea.codeInsight.gradle.matches
import org.jetbrains.kotlin.idea.codeInsight.gradle.parseKotlinVersionRequirement
import org.jetbrains.kotlin.tooling.core.KotlinToolingVersion
import org.jetbrains.plugins.gradle.tooling.annotation.PluginTargetVersions
import org.jetbrains.plugins.gradle.tooling.annotation.TargetVersions
import org.jetbrains.plugins.gradle.tooling.util.VersionMatcher
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.extension.BeforeEachCallback
import org.junit.jupiter.api.extension.ExtensionContext

/**
 * JUnit 5 counterpart of `PluginTargetVersionsRule`.
 *
 * Implemented as a [BeforeEachCallback] (rather than [org.junit.jupiter.api.extension.ExecutionCondition])
 * because `@ParameterizedClass` with the default `PER_METHOD` lifecycle constructs the test instance
 * *after* execution conditions are evaluated — at that point `ExtensionContext.testInstance` is empty
 * and we have no way to read the resolved [KmpVersions] for the current invocation. `BeforeEach` fires
 * after instance construction so we can pull the active versions straight off the instance.
 *
 * The extension locates [KmpVersions] on the test instance via reflection (any field of type
 * [KmpVersions] on the class hierarchy). No marker interface or inheritance is required from the test
 * class — declaring the constructor parameter as `val versions: KmpVersions` is enough.
 *
 * Mutual exclusion with `@TargetVersions` (enforced by the JUnit 4 rule) is preserved.
 */
class PluginTargetVersionsExtension : BeforeEachCallback {
    override fun beforeEach(context: ExtensionContext) {
        val method = context.requiredTestMethod
        val targetVersions = method.getAnnotation(PluginTargetVersions::class.java)
            ?: context.requiredTestClass.getAnnotation(PluginTargetVersions::class.java)
            ?: return

        require(method.getAnnotation(TargetVersions::class.java) == null) {
            "Annotations ${TargetVersions::class.java.name} and ${PluginTargetVersions::class.java.name} can not be used together."
        }

        val versions = findKmpVersionsField(context.requiredTestInstance)
            ?: error(
                "@PluginTargetVersions could not find a KmpVersions property on ${context.requiredTestClass.name}. " +
                        "Declare the parameterized constructor argument as `val versions: KmpVersions`."
            )

        if (!matches(targetVersions, versions)) {
            Assumptions.abort<Unit>(abortReason(versions, targetVersions))
        }
    }

    private fun findKmpVersionsField(instance: Any): KmpVersions? {
        var clazz: Class<*>? = instance.javaClass
        while (clazz != null && clazz != Any::class.java) {
            for (field in clazz.declaredFields) {
                if (field.type == KmpVersions::class.java) {
                    field.isAccessible = true
                    return field.get(instance) as? KmpVersions
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun matches(targetVersions: PluginTargetVersions, versions: KmpVersions): Boolean {
        return matchesKotlin(targetVersions.pluginVersion, versions.kotlin.version) &&
                matchesGradle(targetVersions.gradleVersion, versions.gradle.version)
    }

    private fun matchesKotlin(requirement: String, actual: KotlinToolingVersion): Boolean {
        if (requirement.isEmpty()) return true
        return parseKotlinVersionRequirement(requirement).matches(actual)
    }

    private fun matchesGradle(requirement: String, actual: GradleVersion): Boolean {
        if (requirement.isEmpty()) return true
        // `checkBaseVersions = true` mirrors the JUnit 4 rule, which builds `TargetVersionsImpl` with `checkBaseVersions = true`.
        return VersionMatcher(actual).isVersionMatch(requirement, /* checkBaseVersions = */ true)
    }

    private fun abortReason(versions: KmpVersions, targetVersions: PluginTargetVersions): String =
        "Aborted due to unmet @PluginTargetVersions requirements: " +
                "Gradle ${versions.gradle.version.version} vs '${targetVersions.gradleVersion}', " +
                "Kotlin ${versions.kotlin.version} vs '${targetVersions.pluginVersion}'"
}
