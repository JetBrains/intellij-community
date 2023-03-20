// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.scripting.gradle

import org.jetbrains.kotlin.idea.gradleJava.scripting.GradleKotlinScriptConfigurationInputs
import org.jetbrains.kotlin.idea.gradleJava.scripting.importing.KotlinDslScriptModel
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.GradleBuildRootData
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.readKotlinDslScriptModels
import org.jetbrains.kotlin.idea.gradleJava.scripting.roots.writeKotlinDslScriptModels
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