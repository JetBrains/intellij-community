// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.gradle

import org.jetbrains.kotlin.idea.gradleTooling.arguments.CompilerArgumentsCacheBranchingImpl
import org.jetbrains.kotlin.idea.gradleTooling.arguments.CompilerArgumentsCacheHolder
import org.jetbrains.kotlin.idea.gradleTooling.arguments.CompilerArgumentsCacheMapperImpl
import org.jetbrains.kotlin.idea.test.testFramework.KtUsefulTestCase
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
import kotlin.random.nextInt
import kotlin.test.assertEquals

class CompilerArgumentsCacheTest {
    private lateinit var arguments: List<String>
    private lateinit var mapper: CompilerArgumentsCacheBranchingImpl

    @Before
    fun setUp() {
        arguments = generateCompilerArgumentsList(Random.nextInt(10..30))
        mapper = CompilerArgumentsCacheBranchingImpl()
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

    @Test
    fun `test branched detachable has not own cache`() {
        arguments.forEach { mapper.cacheArgument(it) }
        val detachableMapper = mapper.branchOffDetachable()
        KtUsefulTestCase.assertEmpty(detachableMapper.distributeCacheIds().toList())
    }

    @Test
    fun `test branched detachable caches in master too`() {
        val ids = arguments.map { mapper.cacheArgument(it) }
        val detachableMapper = mapper.branchOffDetachable()
        val uniqueArg = "*UNIQUE*"
        val uniqueArgCache = detachableMapper.cacheArgument(uniqueArg)
        val detachedCacheAware = detachableMapper.detachCacheAware()
        assert(mapper.checkCached(uniqueArg))
        assertEquals(mapper.getCached(uniqueArgCache), uniqueArg)
        KtUsefulTestCase.assertContainsOrdered(mapper.distributeCacheIds().toList(), ids.distinct() + uniqueArgCache)
        KtUsefulTestCase.assertContainsOrdered(detachedCacheAware.distributeCacheIds().toList(), listOf(uniqueArgCache))
    }

    @Test
    fun `test branched detachable does not cache duplicates`() {
        val arguments = generateCompilerArgumentsList(Random.nextInt(30))
        val masterMapper = CompilerArgumentsCacheBranchingImpl()
        arguments.forEach { masterMapper.cacheArgument(it) }
        val detachableMapper = masterMapper.branchOffDetachable()
        arguments.forEach { detachableMapper.cacheArgument(it) }
        val detachedCacheAware = detachableMapper.detachCacheAware()
        KtUsefulTestCase.assertEmpty(detachedCacheAware.distributeCacheIds().toList())
    }

    @Test
    fun `test merged cache matches master cache`() {
        val firstDetachableMapper = mapper.branchOffDetachable()
        generateCompilerArgumentsList(Random.nextInt(10..30)).forEach { firstDetachableMapper.cacheArgument(it) }
        val firstDetachedAware = firstDetachableMapper.detachCacheAware()
        val secondDetachableMapper = mapper.branchOffDetachable()
        generateCompilerArgumentsList(Random.nextInt(10..30)).forEach { secondDetachableMapper.cacheArgument(it) }
        val secondDetachedAware = secondDetachableMapper.detachCacheAware()

        val cacheAwareHolder = CompilerArgumentsCacheHolder()
        cacheAwareHolder.apply {
            mergeCacheAware(firstDetachedAware)
            mergeCacheAware(secondDetachedAware)
        }
        val cacheAware = cacheAwareHolder.getCacheAware(mapper.cacheOriginIdentifier)!!
        val mergedKeys = cacheAware.distributeCacheIds().toList()
        val mergedValues = mergedKeys.sorted().map { cacheAware.getCached(it)!! }
        KtUsefulTestCase.assertContainsElements(mergedKeys, mapper.distributeCacheIds().toList())
        KtUsefulTestCase.assertContainsOrdered(
            mergedValues,
            mapper.distributeCacheIds().toList().sorted().map { mapper.getCached(it)!! })
    }

    @Test
    fun `test merged cache from different masters`() {
        val firstDetachableMapper = mapper.branchOffDetachable()
        generateCompilerArgumentsList(Random.nextInt(10..30)).forEach { firstDetachableMapper.cacheArgument(it) }
        val firstDetachedAware = firstDetachableMapper.detachCacheAware()
        val secondMapper = CompilerArgumentsCacheMapperImpl()
        generateCompilerArgumentsList(Random.nextInt(10..30)).forEach { secondMapper.cacheArgument(it) }

        val cacheAwareHolder = CompilerArgumentsCacheHolder()
        cacheAwareHolder.apply {
            mergeCacheAware(firstDetachedAware)
            mergeCacheAware(secondMapper)
        }
        val firstCacheAware = cacheAwareHolder.getCacheAware(mapper.cacheOriginIdentifier)!!
        var mergedKeys = firstCacheAware.distributeCacheIds().toList()
        var mergedValues = mergedKeys.sorted().map { firstCacheAware.getCached(it)!! }
        KtUsefulTestCase.assertContainsElements(mergedKeys, mapper.distributeCacheIds().toList())
        KtUsefulTestCase.assertContainsOrdered(
            mergedValues,
            mapper.distributeCacheIds().toList().sorted().map { mapper.getCached(it)!! }
        )

        val secondCacheAware = cacheAwareHolder.getCacheAware(secondMapper.cacheOriginIdentifier)!!
        mergedKeys = secondCacheAware.distributeCacheIds().toList()
        mergedValues = mergedKeys.sorted().map { secondCacheAware.getCached(it)!! }
        KtUsefulTestCase.assertContainsElements(mergedKeys, secondMapper.distributeCacheIds().toList())
        KtUsefulTestCase.assertContainsOrdered(
            mergedValues,
            secondMapper.distributeCacheIds().toList().sorted().map { secondMapper.getCached(it)!! }
        )
    }

    @Test
    fun `test detach with fallback strategy`() {
        val firstMapper = mapper.branchOffDetachable(isFallbackStrategy = true)
        val arguments = generateCompilerArgumentsList(Random.nextInt(10..30))
        val cachedArguments1 = arguments.map { firstMapper.cacheArgument(it) }
        val firstDetachedAware = firstMapper.detachCacheAware()
        val secondMapper = mapper.branchOffDetachable(isFallbackStrategy = true)
        val cachedArguments2 = arguments.map { secondMapper.cacheArgument(it) }
        val secondDetachableAware = secondMapper.detachCacheAware()
        KtUsefulTestCase.assertContainsOrdered(cachedArguments1, cachedArguments2)
        KtUsefulTestCase.assertContainsOrdered(
            firstDetachedAware.distributeCacheIds().toList(),
            secondDetachableAware.distributeCacheIds().toList()
        )
        KtUsefulTestCase.assertEmpty(mapper.distributeCacheIds().toList())
    }

    companion object {
        private val alphabet = listOf('a'..'z', 'A'..'Z').map { it.joinToString(separator = "") }.joinToString(separator = "")

        private fun generateCompilerArgument(length: Int): String =
            generateSequence { Random.nextInt(alphabet.indices) }.take(length).map { alphabet[it] }.joinToString("")

        private fun generateCompilerArgumentsList(size: Int): List<String> =
            generateSequence { generateCompilerArgument(Random.nextInt(5..20)) }.take(size).toList()

    }

}