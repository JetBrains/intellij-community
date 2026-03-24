// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.projectStructure

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import org.jetbrains.kotlin.idea.base.platforms.KotlinJavaScriptStdlibDetectorFacility
import org.jetbrains.kotlin.idea.base.platforms.KotlinJvmStdlibDetectorFacility
import kotlin.text.get

enum class BundledLibraryVariant(val displayName: String, val wellKnownCoordinates: Set<String>) {
    Jvm(
        "jvm", setOf(
            "org.jetbrains.kotlin:kotlin-test",
            "org.jetbrains.kotlin:kotlin-test-junit",
            "org.jetbrains.kotlin:kotlin-test-junit5",
            "org.jetbrains.kotlin:kotlin-test-testng",
        )
    ),
    Js("js", setOf("org.jetbrains.kotlin:kotlin-test-js")),
    Native("native", emptySet()),
    Common(
        "common", setOf(
            "org.jetbrains.kotlin:kotlin-test-common",
            "org.jetbrains.kotlin:kotlin-test-annotations-common",
            "org.jetbrains.kotlin:kotlin-test:annotationsCommonMain",
            "org.jetbrains.kotlin:kotlin-test:assertionsCommonMain",
            "org.jetbrains.kotlin:kotlin-stdlib:commonMain",
        )
    )
}

/**
 * Detects libraries that are bundled in the K/N distribution and their variants from maven.
 *
 * For now there are `stdlib` and `kotlin-test`
 */
fun bundledLibraryVariant(
    library: Library,
    isNativeStdLib: Boolean,
    project: Project
): BundledLibraryVariant? {
    val name = library.name.orEmpty().substringBeforeLast(':')
    return when {
        isNativeStdLib -> BundledLibraryVariant.Native
        KotlinJvmStdlibDetectorFacility.isStdlib(project, library) -> BundledLibraryVariant.Jvm
        KotlinJavaScriptStdlibDetectorFacility.isStdlib(project, library) -> BundledLibraryVariant.Js
        else -> BundledLibraryVariant.entries.firstOrNull { bundledLibraryVariant ->
            name in bundledLibraryVariant.wellKnownCoordinates
        }
    }
}

private val GRADLE_LIBRARY_NAME_REGEX: Regex =
    Regex("^(?<prefix>\\S+: )?(?<group>\\S+?):(?<artifactId>\\S+?):((?<variant>\\S+?):)?(?<version>\\S+)$")

// group name constants are not used in the pattern because of the regex checker complains
private const val GROUP = "group"
private const val ARTIFACT_ID = "artifactId"
private const val VARIANT = "variant"

fun extractArtifactIdWithoutVariant(library: String?): String? {
    val match = GRADLE_LIBRARY_NAME_REGEX.matchEntire(library.orEmpty()) ?: return null
    val variant = match.groups[VARIANT]
    val groupId = match.groups[GROUP]?.value ?: return null
    val artifactId = match.groups[ARTIFACT_ID]?.value ?: return null

    val artifactIdWithoutVariant = if (variant != null) artifactId else artifactId.substringBeforeLast('-')
    return "$groupId:$artifactIdWithoutVariant"
}

fun extractLibraryVariantName(library: String?): String? {
    val match = GRADLE_LIBRARY_NAME_REGEX.matchEntire(library.orEmpty()) ?: return null
    val variant = match.groups[VARIANT]
    val artifactId = match.groups[ARTIFACT_ID]?.value ?: return null

    return variant?.value ?:  artifactId.substringAfterLast('-')
}