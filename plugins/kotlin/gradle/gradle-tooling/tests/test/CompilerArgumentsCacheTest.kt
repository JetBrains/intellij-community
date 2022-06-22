// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.idea.gradleTooling.arguments.CompilerArgumentsCacheMapperImpl
import org.jetbrains.kotlin.idea.test.testFramework.KtUsefulTestCase
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
import kotlin.random.nextInt

class CompilerArgumentsCacheTest {
    private lateinit var arguments: List<String>
    private lateinit var mapper: CompilerArgumentsCacheMapperImpl

    @Before
    fun setUp() {
        arguments = generateCompilerArgumentsList(Random.nextInt(10..30))
        mapper = CompilerArgumentsCacheMapperImpl()
    }

    @Test
    fun `test cache arguments and check order of IDs`() {
        val ids = arguments.map { mapper.cacheArgument(it) }
        val cached = ids.mapNotNull { mapper.getCached(it) }
        KtUsefulTestCase.assertContainsOrdered(cached, arguments)
    }

    @Test
    fun `test cache IDs are created only for unique compiler arguments`() {
        repeat(3) { arguments.map { mapper.cacheArgument(it) } }
        val cached = mapper.distributeCacheIds().map { mapper.getCached(it) }
        KtUsefulTestCase.assertContainsElements(cached, arguments)
    }

    companion object {
        private val alphabet = listOf('a'..'z', 'A'..'Z').map { it.joinToString(separator = "") }.joinToString(separator = "")

        private fun generateCompilerArgument(length: Int): String =
            generateSequence { Random.nextInt(alphabet.indices) }.take(length).map { alphabet[it] }.joinToString("")

        private fun generateCompilerArgumentsList(size: Int): List<String> =
            generateSequence { generateCompilerArgument(Random.nextInt(5..20)) }.take(size).toList()

    }

}