// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.artifacts

import com.intellij.openapi.application.PathManager
import com.intellij.util.xml.dom.readXmlAsModel
import org.jetbrains.kotlin.idea.artifacts.KotlinArtifacts.Companion.KOTLIN_MAVEN_GROUP_ID
import org.jetbrains.kotlin.idea.compiler.configuration.KotlinPluginLayout
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.utils.addToStdlib.cast
import java.io.File
import java.nio.file.Paths
import kotlin.io.path.inputStream

object AdditionalKotlinArtifacts {
    val kotlinStdlibCommon: File by lazy {
        getMavenArtifactJarPath(KOTLIN_MAVEN_GROUP_ID, "kotlin-stdlib-common", getJpsLibraryVersion("kotlin_stdlib_jdk8.xml"))
    }

    val kotlinStdlibCommonSources: File by lazy {
        getMavenArtifactJarPath(KOTLIN_MAVEN_GROUP_ID, "kotlin-stdlib-common", getJpsLibraryVersion("kotlin_stdlib_jdk8.xml"))
    }

    val jsr305: File by lazy {
        getMavenArtifactJarPath("com.google.code.findbugs", "jsr305", getJpsLibraryVersion("jsr305.xml"))
    }

    val junit3: File by lazy {
        getMavenArtifactJarPath("junit", "junit", getJpsLibraryVersion("JUnit3.xml"))
    }

    val parcelizeRuntime: File by lazy {
        KotlinArtifacts.instance.kotlincDirectory.resolve("lib/parcelize-runtime.jar").also { check(it.exists()) }
    }

    val androidExtensionsRuntime by lazy {
        KotlinArtifacts.instance.kotlincDirectory.resolve("lib/android-extensions-runtime.jar").also { check(it.exists()) }
    }

    @JvmStatic
    val compilerTestDataDir = run {
        val anyJarInMavenLocal = PathManager.getJarPathForClass(KtElement::class.java)?.let { File(it) } ?: error("Can't find any ")
        val artifactId = "kotlin-compiler-testdata-for-ide"
        // Such a weird algorithm because you can't use getMavenArtifactJarPath in this code. That's the only reliable way to find a
        // maven artifact in Maven local
        val testDataJar = generateSequence(anyJarInMavenLocal) { it.parentFile }
            .map {
                resolveMavenArtifactInMavenRepo(
                    it,
                    KOTLIN_MAVEN_GROUP_ID,
                    artifactId,
                    getJpsLibraryVersion("kotlinc_kotlin_compiler_testdata.xml")
                )
            }
            .firstOrNull { it.exists() }
            ?: error("Can't find $artifactId in maven local")
        lazyUnpackJar(testDataJar, File(PathManager.getCommunityHomePath()).resolve("out").resolve("kotlinc-testdata-2"))
    }

    @JvmStatic
    fun compilerTestData(compilerTestDataPath: String): String {
        return compilerTestDataDir.resolve(compilerTestDataPath).canonicalPath
    }
}

private fun getJpsLibraryVersion(libXmlName: String): String =
    Paths.get(PathManager.getHomePath()).resolve(".idea").resolve("libraries").resolve(libXmlName).inputStream().use(::readXmlAsModel)
        .children.single()
        .children.first()
        .also { check(it.name == "properties") }
        .let { it.getAttributeValue("maven-id") ?: error("Can't find 'maven-id' in '$it'") }
        .substringAfterLast(":")
