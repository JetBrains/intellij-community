// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.k1.gradle

import org.jetbrains.kotlin.gradle.scripting.shared.GradleKotlinScriptConfigurationInputs
import org.jetbrains.kotlin.gradle.scripting.shared.importing.KotlinDslScriptModel
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootData
import org.jetbrains.kotlin.gradle.scripting.shared.roots.readKotlinDslScriptModels
import org.jetbrains.kotlin.gradle.scripting.shared.roots.writeKotlinDslScriptModels
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.test.assertEquals

class GradleBuildRootDataSerializerTest {
    @Test
    fun write() {
        val data = GradleBuildRootData(
            123,
            listOf("a", "b", "c"),
            "gradleHome",
            "javaHome",
            listOf(
                KotlinDslScriptModel(
                    "gradleHome",
                    GradleKotlinScriptConfigurationInputs("b", 1, "a"),
                    listOf("c", "a", "b"),
                    listOf("b", "c", "a"),
                    listOf("i", "c", "b"),
                    listOf()
                ),
                KotlinDslScriptModel(
                    "gradleHome",
                    GradleKotlinScriptConfigurationInputs("b", 1, "a"),
                    listOf("c", "a", "b"),
                    listOf("b", "c", "a"),
                    listOf("i", "c", "b"),
                    listOf()
                )
            )
        )

        val buffer = ByteArrayOutputStream()
        writeKotlinDslScriptModels(DataOutputStream(buffer), data)

        val restored = readKotlinDslScriptModels(DataInputStream(ByteArrayInputStream(buffer.toByteArray())), "a")

        assertEquals(data.toString(), restored.toString())
    }

    @Test
    fun writeNullable() {
        val data = GradleBuildRootData(
            0,
            listOf(),
            "null",
            null,
            listOf()
        )

        val buffer = ByteArrayOutputStream()
        writeKotlinDslScriptModels(DataOutputStream(buffer), data)

        val restored = readKotlinDslScriptModels(DataInputStream(ByteArrayInputStream(buffer.toByteArray())), "a")

        assertEquals(data.toString(), restored.toString())
    }
}