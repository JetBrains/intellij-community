// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.base.test

import com.intellij.pom.java.LanguageLevel
import com.intellij.util.io.exists
import org.jetbrains.kotlin.idea.base.plugin.artifacts.TestKotlinArtifacts
import java.io.File
import java.nio.file.Path

class KotlinTestLibrary private constructor(val name: String, val classes: List<Path>, val sources: List<Path>) {
    companion object {
        fun kotlinStdlibJvm(javaLanguageLevel: LanguageLevel): KotlinTestLibrary = Builder.build("kotlin-stdlib") {
            classes(TestKotlinArtifacts.kotlinStdlib)
            sources(TestKotlinArtifacts.kotlinStdlibSources)

            if (javaLanguageLevel.isAtLeast(LanguageLevel.JDK_1_7)) {
                classes(TestKotlinArtifacts.kotlinStdlibJdk7)
                sources(TestKotlinArtifacts.kotlinStdlibJdk7Sources)
            }

            if (javaLanguageLevel.isAtLeast(LanguageLevel.JDK_1_8)) {
                classes(TestKotlinArtifacts.kotlinStdlibJdk8)
                sources(TestKotlinArtifacts.kotlinStdlibJdk8Sources)
            }
        }

        fun kotlinReflectJvm(): KotlinTestLibrary = Builder.build("kotlin-reflect") {
            classes(TestKotlinArtifacts.kotlinReflect)
            sources(TestKotlinArtifacts.kotlinReflectSources)
        }
    }

    private class Builder(val name: String) {
        private val classes = ArrayList<Path>()
        private val sources = ArrayList<Path>()

        fun classes(path: Path) {
            assert(path.exists())
            classes.add(path)
        }

        fun classes(file: File) {
            classes(file.toPath())
        }

        fun sources(path: Path) {
            assert(path.exists())
            sources.add(path)
        }

        fun sources(file: File) {
            sources(file.toPath())
        }

        fun build(): KotlinTestLibrary {
            return KotlinTestLibrary(name, classes, sources)
        }

        companion object {
            fun build(name: String, block: Builder.() -> Unit) = Builder(name).apply(block).build()
        }
    }
}