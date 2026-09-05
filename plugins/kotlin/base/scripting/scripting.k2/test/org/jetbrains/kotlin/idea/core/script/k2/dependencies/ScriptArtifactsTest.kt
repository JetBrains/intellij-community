// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.core.script.k2.dependencies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.script.experimental.api.DependencyCoordinates
import kotlin.script.experimental.api.DependencyRepository
import kotlin.script.experimental.dependencies.impl.DependenciesResolverOptionsName

class ScriptArtifactsTest {

    @Test
    fun `plain coordinates are accepted`() {
        val properties = "org.apache.commons:commons-text:1.12.0".toRepositoryLibraryProperties(transitive = true)
        val descriptor = properties?.repositoryLibraryDescriptor
        assertEquals("org.apache.commons", descriptor?.groupId)
        assertEquals("commons-text", descriptor?.artifactId)
        assertEquals("1.12.0", descriptor?.version)
        assertEquals("jar", descriptor?.packaging)
        assertTrue(descriptor?.isIncludeTransitiveDependencies == true)
    }

    @Test
    fun `transitive flag is passed through`() {
        val descriptor = "org.apache.commons:commons-text:1.12.0"
            .toRepositoryLibraryProperties(transitive = false)?.repositoryLibraryDescriptor
        assertTrue(descriptor?.isIncludeTransitiveDependencies == false)
    }

    @Test
    fun `explicit packaging is accepted when the IDE knows it`() {
        val descriptor = "com.example:thing:pom:1.0".toRepositoryLibraryProperties(transitive = true)?.repositoryLibraryDescriptor
        assertEquals("pom", descriptor?.packaging)
        assertEquals("1.0", descriptor?.version)
    }

    @Test
    fun `unknown packaging is rejected rather than silently resolving nothing`() {
        assertNull("com.example:thing:tar.gz:1.0".toRepositoryLibraryProperties(transitive = true))
    }

    @Test
    fun `classifier is rejected`() {
        assertNull("org.lwjgl:lwjgl:jar:natives-macos:3.3.3".toRepositoryLibraryProperties(transitive = true))
    }

    @Test
    fun `malformed coordinates are rejected`() {
        assertNull("not-coordinates".toRepositoryLibraryProperties(transitive = true))
        assertNull("group:artifact".toRepositoryLibraryProperties(transitive = true))
    }

    @Test
    fun `repository id defaults to a file-name-safe form of the url`() {
        val repository = DependencyRepository("https://example.com/repo").toScriptArtifactRepository()
        assertEquals("https___example.com_repo", repository.id)
        assertEquals("https://example.com/repo", repository.url)
    }

    @Test
    fun `explicit repository id wins`() {
        val repository = DependencyRepository(
            "https://example.com/repo",
            mapOf(DependenciesResolverOptionsName.MAVEN_REPOSITORY_ID.key to "internal"),
        ).toScriptArtifactRepository()
        assertEquals("internal", repository.id)
    }

    @Test
    fun `credentials support the environment variable indirection`() {
        val name = System.getenv().keys.firstOrNull() ?: return
        val repository = DependencyRepository(
            "https://example.com/repo",
            mapOf(
                DependenciesResolverOptionsName.USERNAME.key to "$$name",
                DependenciesResolverOptionsName.PASSWORD.key to "literal",
            ),
        ).toScriptArtifactRepository()

        assertEquals(System.getenv(name), repository.username)
        assertEquals("literal", repository.password)
    }

    @Test
    fun `credentials from an unset environment variable are left unresolved`() {
        val repository = DependencyRepository(
            "https://example.com/repo",
            mapOf(DependenciesResolverOptionsName.USERNAME.key to "\$DEFINITELY_NOT_SET_FOR_THIS_TEST"),
        ).toScriptArtifactRepository()
        assertNull(repository.username)
    }

    @Test
    fun `the source location is not part of a request's identity`() {
        val dependency = DependencyCoordinates(listOf("g:a:1"))
        val sameFromElsewhere = dependency.copy(
            sourceCodeLocation = kotlin.script.experimental.api.SourceCode.LocationWithId(
                "other.main.kts",
                kotlin.script.experimental.api.SourceCode.Location(kotlin.script.experimental.api.SourceCode.Position(1, 1)),
            )
        )
        assertEquals(dependency.toRequest(emptyList()), sameFromElsewhere.toRequest(emptyList()))
    }

    @Test
    fun `options the IDE cannot express are reported`() {
        val withClassifier = DependencyCoordinates(
            listOf("g:a:1"),
            mapOf(DependenciesResolverOptionsName.CLASSIFIER.key to "natives"),
        ).toRequest(emptyList())
        assertEquals(listOf("classifier=natives"), withClassifier.unsupportedOptions)

        assertTrue(DependencyCoordinates(listOf("g:a:1")).toRequest(emptyList()).unsupportedOptions.isEmpty())
    }

    @Test
    fun `transitive resolution is the default`() {
        assertTrue(DependencyCoordinates(listOf("g:a:1")).toRequest(emptyList()).isTransitive)
        assertTrue(
            DependencyCoordinates(listOf("g:a:1"), mapOf(DependenciesResolverOptionsName.TRANSITIVE.key to "false"))
                .toRequest(emptyList()).isTransitive.not()
        )
    }
}
