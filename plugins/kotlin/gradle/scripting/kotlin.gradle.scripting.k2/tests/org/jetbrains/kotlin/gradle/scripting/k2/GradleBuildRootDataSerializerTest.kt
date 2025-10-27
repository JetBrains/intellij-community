// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.gradle.scripting.k2

import org.jetbrains.kotlin.gradle.scripting.k2.roots.GradleBuildRootDataSerializer
import org.jetbrains.kotlin.gradle.scripting.shared.GradleKotlinScriptConfigurationInputs
import org.jetbrains.kotlin.gradle.scripting.shared.importing.KotlinDslScriptModel
import org.jetbrains.kotlin.gradle.scripting.shared.roots.GradleBuildRootData
import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class GradleBuildRootDataSerializerTest {
    @Test
    fun write() {
        val dataToWrite = GradleBuildRootData(
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

        val dataToRead = GradleBuildRootData(
            123,
            listOf("a", "b", "c"),
            "gradleHome",
            "javaHome",
            listOf()
        )

        val buffer = ByteArrayOutputStream()
        GradleBuildRootDataSerializer.writeKotlinDslScriptModels(DataOutputStream(buffer), dataToWrite)

        val restored = GradleBuildRootDataSerializer.readKotlinDslScriptModels(DataInputStream(ByteArrayInputStream(buffer.toByteArray())))

        assertEquals(dataToRead.toString(), restored.toString())
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
        GradleBuildRootDataSerializer.writeKotlinDslScriptModels(DataOutputStream(buffer), data)

        val restored = GradleBuildRootDataSerializer.readKotlinDslScriptModels(DataInputStream(ByteArrayInputStream(buffer.toByteArray())))

        assertEquals(data.toString(), restored.toString())
    }
}